package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.dto.ImportResultDTO;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.entity.DeviceImage;
import com.gzhu.equipment.mapper.DeviceImageMapper;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.service.CategoryService;
import com.gzhu.equipment.service.DeviceImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备批量导入服务 — CSV / XLSX / XLS 智能导入
 *
 * <h3>CSV 编码自动检测</h3>
 * 中文 Windows Excel 导出 CSV 默认 GBK 编码。
 * 本实现通过 BOM 头 + 尝试验证两种方式自动选择正确编码：
 * <ol>
 *   <li>有 BOM(0xEF 0xBB 0xBF) → UTF-8</li>
 *   <li>UTF-8 解码后验证首行表头是否含关键中文字段 → 无乱码则 UTF-8</li>
 *   <li>否则 → GBK</li>
 * </ol>
 *
 * <h3>Excel 格式兼容</h3>
 * 同时支持 .xlsx (XSSFWorkbook) 和 .xls (HSSFWorkbook)。
 *
 * <h3>智能导入策略</h3>
 * 以 asset_no 为主键，分三步：
 * <ol>
 *   <li>扫描新文件中所有 asset_no，构建集合</li>
 *   <li>删除旧数据中不在新集合中的记录（保留关联的图片/借用记录）</li>
 *   <li>逐行处理：已有记录→更新业务字段保留关联数据；新记录→插入</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceImportServiceImpl implements DeviceImportService {

    private final DeviceMapper deviceMapper;
    private final DeviceImageMapper deviceImageMapper;
    private final CategoryService categoryService;

    private static final int BATCH_SIZE = 200;
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    // 用于编码检测的中文关键词（表头中一定包含）
    private static final String ENCODING_CHECK_WORD = "资产编号";

    @Override
    @Transactional
    public ImportResultDTO importFromStream(InputStream inputStream, String fileName, Long userId) {
        String batchId = UUID.randomUUID().toString().substring(0, 8);
        String lowerName = fileName != null ? fileName.toLowerCase() : "";

        log.info("开始批量导入: fileName={} batchId={} userId={}", fileName, batchId, userId);

        ImportResultDTO result;
        if (lowerName.endsWith(".csv")) {
            result = importCsv(inputStream, userId, batchId);
        } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            result = importExcel(inputStream, userId, batchId, lowerName);
        } else {
            throw new IllegalArgumentException("不支持的文件格式，仅支持 .csv / .xlsx / .xls");
        }

        log.info("导入完成: batchId={} total={} new={} update={} delete={} fail={} autoCate={} uncate={}",
                batchId, result.getTotalRows(), result.getSuccessCount(), result.getUpdateCount(),
                result.getDeleteCount(), result.getFailCount(),
                result.getAutoCategoryCount(), result.getUncategorizedCount());
        return result;
    }

    @Override
    public ImportResultDTO dryRun(InputStream inputStream, String fileName) {
        String batchId = "DRY-RUN-" + UUID.randomUUID().toString().substring(0, 6);
        String lowerName = fileName != null ? fileName.toLowerCase() : "";

        ImportResultDTO result = ImportResultDTO.builder()
                .batchId(batchId)
                .errors(new ArrayList<>())
                .build();

        List<String[]> parsedRows = new ArrayList<>();

        if (lowerName.endsWith(".csv")) {
            parsedRows = parseCsvForDryRun(inputStream);
        } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            parsedRows = parseExcelForDryRun(inputStream, lowerName);
        }

        // totalRows显示文件实际总行数（不限20）
        result.setTotalRows(parsedRows.size());
        log.info("DryRun: 文件共{}行数据，预览处理前{}行", parsedRows.size(), Math.min(20, parsedRows.size()));

        // 只预览处理前20行，但totalRows已反映全量
        int previewLimit = Math.min(parsedRows.size(), 20);
        for (int i = 0; i < previewLimit; i++) {
            String[] cols = parsedRows.get(i);
            Device device = mapColumns(cols, null, batchId);
            if (device == null) continue;
            Long categoryId = categoryService.classifyByGbName(device.getGbCategoryName());
            if (categoryId != null) {
                device.setCategoryId(categoryId);
                result.setAutoCategoryCount(result.getAutoCategoryCount() + 1);
            } else {
                device.setCategoryId(10L);
                result.setUncategorizedCount(result.getUncategorizedCount() + 1);
            }
        }

        return result;
    }

    @Override
    public int clearByBatchId(String batchId) {
        log.info("清除导入批次: batchId={}", batchId);
        return deviceMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Device>()
                        .eq("import_batch_id", batchId));
    }

    // ==================== CSV 智能导入 ====================

    /**
     * 读取 CSV 所有行（自动检测编码），构建 newAssetNoSet，删除旧数据中不存在的记录，再逐行 upsert
     */
    private ImportResultDTO importCsv(InputStream inputStream, Long userId, String batchId) {
        ImportResultDTO result = ImportResultDTO.builder()
                .batchId(batchId)
                .errors(new ArrayList<>())
                .build();

        // 1. 将所有行读入内存（自动检测编码）
        List<String[]> allRows = parseCsvWithEncodingDetection(inputStream, result);
        if (allRows.isEmpty()) {
            if (result.getErrors().isEmpty()) {
                result.addError(0, "-", "-", "文件中无有效数据行（至少需要资产编号列）");
            }
            return result;
        }

        log.info("CSV解析完成: 共 {} 行", allRows.size());

        // 2. 收集新文件中的 asset_no 集合
        Set<String> newAssetNos = new HashSet<>();
        for (String[] cols : allRows) {
            String assetNo = cols.length > 1 ? cols[1] : null;
            if (assetNo != null && !assetNo.trim().isEmpty()) {
                newAssetNos.add(assetNo.trim());
            }
        }

        // 3. 删除旧数据中不在新文件中的记录
        int deleted = deleteDevicesNotInSet(newAssetNos);
        result.setDeleteCount(deleted);
        log.info("已删除 {} 条旧数据中不存在的设备记录", deleted);

        // 4. 逐行 upsert
        List<Device> batch = new ArrayList<>(BATCH_SIZE);
        for (String[] cols : allRows) {
            try {
                Device device = mapColumns(cols, userId, batchId);
                if (device == null) continue;

                // 自动分类
                Long categoryId = categoryService.classifyByGbName(device.getGbCategoryName());
                if (categoryId != null) {
                    device.setCategoryId(categoryId);
                    result.setAutoCategoryCount(result.getAutoCategoryCount() + 1);
                } else {
                    device.setCategoryId(10L);
                    result.setUncategorizedCount(result.getUncategorizedCount() + 1);
                }

                batch.add(device);
                result.setTotalRows(result.getTotalRows() + 1);

                if (batch.size() >= BATCH_SIZE) {
                    smartFlushBatch(batch, result);
                    batch.clear();
                }
            } catch (Exception e) {
                result.setFailCount(result.getFailCount() + 1);
                result.addError(0, cols.length > 1 ? cols[1] : "", cols.length > 2 ? cols[2] : "",
                        e.getMessage());
            }
        }

        if (!batch.isEmpty()) {
            smartFlushBatch(batch, result);
        }

        return result;
    }

    // ==================== Excel 智能导入 ====================

    private ImportResultDTO importExcel(InputStream inputStream, Long userId, String batchId, String lowerName) {
        ImportResultDTO result = ImportResultDTO.builder()
                .batchId(batchId)
                .errors(new ArrayList<>())
                .build();

        List<String[]> allRows = parseExcelAll(inputStream, lowerName, result);
        if (allRows.isEmpty()) {
            if (result.getErrors().isEmpty()) {
                result.addError(0, "-", "-", "文件中无有效数据行");
            }
            return result;
        }

        log.info("Excel解析完成: 共 {} 行", allRows.size());

        // 收集 asset_no 集合
        Set<String> newAssetNos = new HashSet<>();
        for (String[] cols : allRows) {
            String assetNo = cols.length > 1 ? cols[1] : null;
            if (assetNo != null && !assetNo.trim().isEmpty()) {
                newAssetNos.add(assetNo.trim());
            }
        }

        // 删除旧数据
        int deleted = deleteDevicesNotInSet(newAssetNos);
        result.setDeleteCount(deleted);
        log.info("已删除 {} 条旧数据中不存在的设备记录", deleted);

        // 逐行 upsert
        List<Device> batch = new ArrayList<>(BATCH_SIZE);
        for (String[] cols : allRows) {
            try {
                Device device = mapColumns(cols, userId, batchId);
                if (device == null) continue;

                Long categoryId = categoryService.classifyByGbName(device.getGbCategoryName());
                if (categoryId != null) {
                    device.setCategoryId(categoryId);
                    result.setAutoCategoryCount(result.getAutoCategoryCount() + 1);
                } else {
                    device.setCategoryId(10L);
                    result.setUncategorizedCount(result.getUncategorizedCount() + 1);
                }

                batch.add(device);
                result.setTotalRows(result.getTotalRows() + 1);

                if (batch.size() >= BATCH_SIZE) {
                    smartFlushBatch(batch, result);
                    batch.clear();
                }
            } catch (Exception e) {
                result.setFailCount(result.getFailCount() + 1);
                result.addError(0, cols.length > 1 ? cols[1] : "", cols.length > 2 ? cols[2] : "",
                        e.getMessage());
            }
        }

        if (!batch.isEmpty()) {
            smartFlushBatch(batch, result);
        }

        return result;
    }

    // ==================== 智能 Upsert ====================

    /**
     * 智能批量写入：已有记录→更新业务字段但保留关联数据；新记录→插入
     */
    private void smartFlushBatch(List<Device> batch, ImportResultDTO result) {
        for (Device device : batch) {
            try {
                Device existing = deviceMapper.selectByAssetNo(device.getAssetNo());
                if (existing != null) {
                    // 已存在 → 更新业务字段，保留关联数据
                    device.setId(existing.getId());

                    // 保留旧记录中用户维护的字段（不覆盖）
                    if (device.getBorrowType() == null) device.setBorrowType(existing.getBorrowType());
                    if (device.getLaboratoryId() == null) device.setLaboratoryId(existing.getLaboratoryId());
                    if (device.getDescription() == null) device.setDescription(existing.getDescription());
                    // 保留封面图（新数据通常没有封面图）
                    if (device.getCoverImage() == null) device.setCoverImage(existing.getCoverImage());
                    // 保留 defaultApproverId
                    if (device.getDefaultApproverId() == null)
                        device.setDefaultApproverId(existing.getDefaultApproverId());

                    deviceMapper.updateById(device);
                    result.setUpdateCount(result.getUpdateCount() + 1);
                } else {
                    // 新记录 → 插入，缺失值使用默认值
                    if (device.getBorrowType() == null) device.setBorrowType(2);
                    if (device.getBorrowStatus() == null) device.setBorrowStatus(1);
                    if (device.getDeviceStatus() == null) device.setDeviceStatus(1);
                    if (device.getTotalQty() == null) device.setTotalQty(1);
                    if (device.getAvailableQty() == null) device.setAvailableQty(device.getTotalQty());

                    deviceMapper.insert(device);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                }
            } catch (Exception e) {
                result.setFailCount(result.getFailCount() + 1);
                result.addError(0, device.getAssetNo(), device.getName(), e.getMessage());
                log.warn("导入记录失败: assetNo={} name={} error={}",
                        device.getAssetNo(), device.getName(), e.getMessage());
            }
        }
    }

    /**
     * 删除数据库中 asset_no 不在给定集合中的设备记录
     */
    private int deleteDevicesNotInSet(Set<String> newAssetNos) {
        if (newAssetNos.isEmpty()) return 0;

        // 查出现有所有 asset_no
        List<Device> allExisting = deviceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Device>()
                        .select("id", "asset_no")
                        .isNotNull("asset_no"));

        int deleted = 0;
        for (Device existing : allExisting) {
            if (!newAssetNos.contains(existing.getAssetNo())) {
                deviceMapper.deleteById(existing.getId());
                deleted++;
            }
        }
        return deleted;
    }

    // ==================== CSV 编码检测 ====================

    /**
     * 自动检测 CSV 编码并解析所有行
     * 顺序：BOM检测 → UTF-8尝试验证 → GBK回退
     */
    private List<String[]> parseCsvWithEncodingDetection(InputStream inputStream, ImportResultDTO result) {
        try {
            byte[] bytes = readAllBytes(inputStream);
            if (bytes.length == 0) return Collections.emptyList();

            String content;
            String detectedEncoding;

            // 1. 检查 BOM (UTF-8: EF BB BF)
            if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                    && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                content = new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
                detectedEncoding = "UTF-8-BOM";
            } else {
                // 2. 尝试 UTF-8
                content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                // 验证：表头是否包含中文关键词且无乱码
                if (content.contains(ENCODING_CHECK_WORD)) {
                    detectedEncoding = "UTF-8";
                } else {
                    // 3. 回退 GBK
                    content = new String(bytes, java.nio.charset.Charset.forName("GBK"));
                    detectedEncoding = "GBK";
                }
            }

            log.info("CSV编码检测: {} (文件大小 {} KB)", detectedEncoding, bytes.length / 1024);

            List<String[]> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
                // 跳过表头
                String header = reader.readLine();
                if (header == null) return rows;

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] cols = parseCsvLineToArray(line);
                    if (cols.length < 2) continue; // 至少需要资产编号
                    rows.add(cols);
                }
            }

            return rows;
        } catch (IOException e) {
            log.error("CSV解析失败: {}", e.getMessage(), e);
            result.addError(0, "-", "-", "CSV文件解析失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== Excel 解析（全部行） ====================

    /**
     * 放宽Zip Bomb检测阈值 — XLSX含大量格式化空单元格时styles.xml压缩率极高会被误判
     */
    private static final double MIN_INFLATE_RATIO = 0.001;

    private List<String[]> parseExcelAll(InputStream inputStream, String lowerName, ImportResultDTO result) {
        try {
            byte[] bytes = readAllBytes(inputStream);
            if (bytes.length == 0) return Collections.emptyList();

            // 放宽Zip Bomb检测阈值（默认0.01，对含大量格式化的资产导出文件不够）
            org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);

            Workbook workbook;
            try {
                workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
                log.info("Excel格式: XLSX");
            } catch (Exception e) {
                // 尝试 HSSF (.xls) 格式
                try {
                    workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes));
                    log.info("Excel格式: XLS (HSSF)");
                } catch (Exception ex) {
                    log.error("无法识别Excel格式，既不是XLSX也不是XLS: {}", ex.getMessage());
                    result.addError(0, "-", "-", "无法识别文件格式，请确认是有效的 .xlsx 或 .xls 文件");
                    return Collections.emptyList();
                }
            }

            List<String[]> rows = new ArrayList<>();
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            if (lastRow < 1) {
                workbook.close();
                log.warn("Excel工作表为空或无数据行: lastRow={}", lastRow);
                return rows;
            }

            for (int i = 1; i <= lastRow; i++) { // 跳过表头（row 0）
                Row row = sheet.getRow(i);
                if (row == null) continue;

                int lastCol = row.getLastCellNum();
                if (lastCol < 2) continue; // 至少需要前两列
                String[] cols = new String[Math.max(lastCol, 49)];
                for (int c = 0; c < lastCol; c++) {
                    cols[c] = getCellString(row.getCell(c));
                }
                rows.add(cols);
            }

            workbook.close();
            log.info("Excel解析完成: {} 行数据", rows.size());
            return rows;
        } catch (IOException e) {
            log.error("Excel解析失败: {}", e.getMessage(), e);
            result.addError(0, "-", "-", "Excel文件解析失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== Dry-Run 解析 ====================

    private List<String[]> parseCsvForDryRun(InputStream inputStream) {
        try {
            byte[] bytes = readAllBytes(inputStream);
            String content;
            if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                    && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                content = new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                if (!content.contains(ENCODING_CHECK_WORD)) {
                    content = new String(bytes, java.nio.charset.Charset.forName("GBK"));
                }
            }
            List<String[]> rows = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
                reader.readLine(); // skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] cols = parseCsvLineToArray(line);
                    if (cols.length >= 2) rows.add(cols);
                }
            }
            return rows;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private List<String[]> parseExcelForDryRun(InputStream inputStream, String lowerName) {
        ImportResultDTO dummy = ImportResultDTO.builder().errors(new ArrayList<>()).build();
        List<String[]> all = parseExcelAll(inputStream, lowerName, dummy);
        // 返回全部行，由dryRun方法控制预览20行和totalRows
        return all;
    }

    // ==================== 列映射（String[] 版本） ====================

    private Device mapColumns(String[] cols, Long userId, String batchId) {
        String assetNo = getArrCol(cols, 1);
        if (assetNo == null || assetNo.trim().isEmpty()) return null;

        Device d = new Device();
        d.setAssetNo(assetNo.trim());
        d.setName(getArrCol(cols, 2));
        d.setModel(getArrCol(cols, 3));
        d.setSpecs(getArrCol(cols, 4));
        d.setTotalQty(parseIntSafe(getArrCol(cols, 5), 1));
        d.setAvailableQty(parseIntSafe(getArrCol(cols, 5), 1)); // 初始可借量=总量
        d.setUnitPrice(parseBigDecimalSafe(getArrCol(cols, 6)));
        d.setTotalAmount(parseBigDecimalSafe(getArrCol(cols, 7)));
        d.setPurchaseDate(parseExcelDate(getArrCol(cols, 8)));
        d.setDepartment(getArrCol(cols, 9));
        d.setCustodian(getArrCol(cols, 10));
        d.setLocation(getArrCol(cols, 12));
        d.setDescription(getArrCol(cols, 13));
        d.setEduCategoryName(getArrCol(cols, 19));
        d.setEduCategoryCode(getArrCol(cols, 20));
        d.setGbCategoryName(getArrCol(cols, 21));
        d.setGbCategoryCode(getArrCol(cols, 22));
        d.setManufacturer(getArrCol(cols, 24));
        d.setSupplier(getArrCol(cols, 31));
        d.setInvoiceNo(getArrCol(cols, 32));
        d.setContractNo(getArrCol(cols, 33));
        d.setWarrantyPeriod(parseIntSafe(getArrCol(cols, 36), null));
        d.setImportBatchId(batchId);
        d.setBorrowStatus(1);  // 默认：可借用
        d.setDeviceStatus(1);  // 默认：正常
        d.setBorrowType(2);  // 默认：可借出
        d.setCreateBy(userId);
        return d;
    }

    private String getArrCol(String[] cols, int index) {
        if (index < cols.length) {
            String val = cols[index];
            return (val != null && !val.trim().isEmpty()) ? val.trim() : null;
        }
        return null;
    }

    // ==================== CSV 行解析 ====================

    /**
     * 解析一行CSV为字符串数组（不含引号），比 List 版本性能更好
     */
    private String[] parseCsvLineToArray(String line) {
        List<String> result = new ArrayList<>(50);
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    // ==================== 类型转换工具 ====================

    private Integer parseIntSafe(String val, Integer defaultVal) {
        if (val == null) return defaultVal;
        try {
            String clean = val.replace(",", "").replace("，", "").replace("\"", "").trim();
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private BigDecimal parseBigDecimalSafe(String val) {
        if (val == null) return null;
        try {
            String clean = val.replace(",", "").replace("，", "").replace("\"", "").trim();
            if (clean.isEmpty()) return null;
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseExcelDate(String val) {
        if (val == null || val.isEmpty()) return null;
        try {
            double d = Double.parseDouble(val.replace(",", "").trim());
            // Excel序列号：整数部分=日期，小数部分=时间
            return EXCEL_EPOCH.plusDays((long) d);
        } catch (NumberFormatException ignored) {
            try {
                return LocalDate.parse(val.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException ignored2) {
                try {
                    return LocalDate.parse(val.trim(), DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                } catch (DateTimeParseException ignored3) {
                    return null;
                }
            }
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    // XLSX日期单元格：格式化为 yyyy-MM-dd 字符串
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                    }
                    double dv = cell.getNumericCellValue();
                    if (dv == Math.floor(dv) && !Double.isInfinite(dv)) {
                        return String.valueOf((long) dv);
                    }
                    return new BigDecimal(dv).toPlainString();
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    // 公式单元格：先尝试拿缓存的字符串结果，不行再拿数值结果
                    try {
                        String sv = cell.getStringCellValue();
                        if (sv != null) return sv;
                    } catch (Exception ignored) {}
                    try {
                        double nv = cell.getNumericCellValue();
                        if (DateUtil.isCellDateFormatted(cell)) {
                            return new java.text.SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                        }
                        return String.valueOf((long) nv);
                    } catch (Exception ignored) {}
                    return "";
                case BLANK:
                    return "";
                default:
                    return "";
            }
        } catch (Exception e) {
            log.warn("读取单元格时出错(CellType={}): {}", cell.getCellType(), e.getMessage());
            return "";
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = inputStream.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }
}
