package com.gzhu.equipment.service.impl;

import com.gzhu.equipment.dto.ImportResultDTO;
import com.gzhu.equipment.entity.Device;
import com.gzhu.equipment.mapper.DeviceMapper;
import com.gzhu.equipment.service.CategoryService;
import com.gzhu.equipment.service.DeviceImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 设备批量导入服务 — 支持 CSV（UTF-8）和 XLSX 格式
 *
 * CSV 列映射（基于学校资产系统导出的49列）：
 * col[2]  = assetNo         资产编号
 * col[3]  = name            资产名称
 * col[4]  = model           型号/品牌
 * col[5]  = specs           规格
 * col[6]  = totalQty        数量
 * col[7]  = unitPrice       单价
 * col[8]  = totalAmount     金额
 * col[9]  = purchaseDate    购置日期（Excel序列号格式）
 * col[10] = department      使用单位
 * col[11] = custodian       使用人
 * col[13] = location        存放地
 * col[14] = description     备注
 * col[20] = eduCategoryName 教育分类名
 * col[22] = gbCategoryName  国标分类名
 * col[23] = gbCategoryCode  国标分类号
 * col[25] = manufacturer    厂家
 * col[32] = supplier        供货商
 * col[33] = invoiceNo       发票号
 * col[34] = contractNo      合同号
 * col[37] = warrantyPeriod  保修期限
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceImportServiceImpl implements DeviceImportService {

    private final DeviceMapper deviceMapper;
    private final CategoryService categoryService;

    private static final int BATCH_SIZE = 200;

    // Excel 日期基准：1900-01-01 = 1
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    @Override
    public ImportResultDTO importFromStream(InputStream inputStream, String fileName, Long userId) {
        String batchId = UUID.randomUUID().toString().substring(0, 8);
        String lowerName = fileName != null ? fileName.toLowerCase() : "";

        if (lowerName.endsWith(".csv")) {
            return importCsv(inputStream, userId, batchId);
        } else if (lowerName.endsWith(".xlsx")) {
            return importXlsx(inputStream, userId, batchId);
        } else {
            throw new IllegalArgumentException("不支持的文件格式，仅支持 .csv 和 .xlsx");
        }
    }

    @Override
    public ImportResultDTO dryRun(InputStream inputStream, String fileName) {
        String batchId = "DRY-RUN-" + UUID.randomUUID().toString().substring(0, 6);
        String lowerName = fileName != null ? fileName.toLowerCase() : "";

        if (lowerName.endsWith(".csv")) {
            return dryRunCsv(inputStream, batchId);
        } else if (lowerName.endsWith(".xlsx")) {
            return dryRunXlsx(inputStream, batchId);
        } else {
            throw new IllegalArgumentException("不支持的文件格式，仅支持 .csv 和 .xlsx");
        }
    }

    @Override
    public int clearByBatchId(String batchId) {
        log.info("清除导入批次: batchId={}", batchId);
        return deviceMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Device>()
                        .eq(Device::getImportBatchId, batchId)
        );
    }

    // ==================== Dry-Run 预览 ====================

    private ImportResultDTO dryRunCsv(InputStream inputStream, String batchId) {
        ImportResultDTO result = createDryRunResult(batchId);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
            reader.readLine(); // 跳过表头
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                List<String> cols = parseCsvLine(line);
                if (cols.size() < 5) continue;
                Device device = mapColumns(cols, null, batchId);
                if (device == null) continue;
                classifyAndRecord(device, result);
                if (result.getTotalRows() >= 20) break; // 只取前20条预览
            }
        } catch (IOException e) {
            result.addError(0, "-", "-", "文件读取失败: " + e.getMessage());
        }
        return result;
    }

    private ImportResultDTO dryRunXlsx(InputStream inputStream, String batchId) {
        ImportResultDTO result = createDryRunResult(batchId);
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = Math.min(sheet.getLastRowNum(), 21); // 最多读21行（含表头）
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                List<String> cols = new ArrayList<>();
                int lastCol = Math.max(row.getLastCellNum(), 49);
                for (int c = 0; c < lastCol; c++) {
                    cols.add(getCellString(row.getCell(c)));
                }
                Device device = mapColumns(cols, null, batchId);
                if (device == null) continue;
                classifyAndRecord(device, result);
                if (result.getTotalRows() >= 20) break;
            }
        } catch (IOException e) {
            result.addError(0, "-", "-", "文件读取失败: " + e.getMessage());
        }
        return result;
    }

    private ImportResultDTO createDryRunResult(String batchId) {
        ImportResultDTO result = ImportResultDTO.builder()
                .batchId(batchId)
                .errors(new ArrayList<>())
                .build();
        return result;
    }

    private void classifyAndRecord(Device device, ImportResultDTO result) {
        Long categoryId = categoryService.classifyByGbName(device.getGbCategoryName());
        if (categoryId != null) {
            device.setCategoryId(categoryId);
            result.setAutoCategoryCount(result.getAutoCategoryCount() + 1);
        } else {
            device.setCategoryId(10L);
            result.setUncategorizedCount(result.getUncategorizedCount() + 1);
        }
        result.setTotalRows(result.getTotalRows() + 1);
    }

    // ==================== CSV 导入 ====================

    private ImportResultDTO importCsv(InputStream inputStream, Long userId, String batchId) {
        ImportResultDTO result = ImportResultDTO.builder()
                .batchId(batchId)
                .errors(new ArrayList<>())
                .build();

        List<Device> batch = new ArrayList<>(BATCH_SIZE);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {

            // 跳过表头
            String header = reader.readLine();
            if (header == null) {
                result.setFailCount(1);
                result.addError(0, "-", "-", "文件为空");
                return result;
            }

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                try {
                    List<String> cols = parseCsvLine(line);
                    if (cols.size() < 5) continue; // 空行跳过

                    Device device = mapColumns(cols, userId, batchId);
                    if (device == null) continue; // asset_no为空跳过

                    // 自动分类
                    Long categoryId = categoryService.classifyByGbName(device.getGbCategoryName());
                    if (categoryId != null) {
                        device.setCategoryId(categoryId);
                        result.setAutoCategoryCount(result.getAutoCategoryCount() + 1);
                    } else {
                        // 未命中 → 归入"其他设备" (category_id = 10)
                        device.setCategoryId(10L);
                        result.setUncategorizedCount(result.getUncategorizedCount() + 1);
                    }

                    batch.add(device);
                    result.setTotalRows(result.getTotalRows() + 1);

                    if (batch.size() >= BATCH_SIZE) {
                        flushBatch(batch, result);
                        batch.clear();
                    }
                } catch (Exception e) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(rowNum, "", "", e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                flushBatch(batch, result);
            }

        } catch (IOException e) {
            log.error("读取CSV文件失败: {}", e.getMessage(), e);
            result.addError(0, "-", "-", "文件读取失败: " + e.getMessage());
        }

        log.info("CSV导入完成: batchId={} total={} success={} update={} fail={} autoCate={} uncate={}",
                batchId, result.getTotalRows(), result.getSuccessCount(), result.getUpdateCount(),
                result.getFailCount(), result.getAutoCategoryCount(), result.getUncategorizedCount());
        return result;
    }

    // ==================== XLSX 导入 ====================

    private ImportResultDTO importXlsx(InputStream inputStream, Long userId, String batchId) {
        ImportResultDTO result = ImportResultDTO.builder()
                .batchId(batchId)
                .errors(new ArrayList<>())
                .build();

        List<Device> batch = new ArrayList<>(BATCH_SIZE);

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) { // 跳过表头（row 0）
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    List<String> cols = new ArrayList<>();
                    int lastCol = row.getLastCellNum();
                    for (int c = 0; c < Math.max(lastCol, 49); c++) {
                        Cell cell = row.getCell(c);
                        cols.add(getCellString(cell));
                    }

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
                        flushBatch(batch, result);
                        batch.clear();
                    }
                } catch (Exception e) {
                    result.setFailCount(result.getFailCount() + 1);
                    result.addError(i + 1, "", "", e.getMessage());
                }
            }

            if (!batch.isEmpty()) {
                flushBatch(batch, result);
            }

        } catch (IOException e) {
            log.error("读取XLSX文件失败: {}", e.getMessage(), e);
            result.addError(0, "-", "-", "文件读取失败: " + e.getMessage());
        }

        log.info("XLSX导入完成: batchId={} total={} success={} update={} fail={}",
                batchId, result.getTotalRows(), result.getSuccessCount(),
                result.getUpdateCount(), result.getFailCount());
        return result;
    }

    // ==================== 批量写入 ====================

    private void flushBatch(List<Device> batch, ImportResultDTO result) {
        for (Device device : batch) {
            try {
                Device existing = deviceMapper.selectByAssetNo(device.getAssetNo());
                if (existing != null) {
                    device.setId(existing.getId());
                    deviceMapper.updateById(device);
                    result.setUpdateCount(result.getUpdateCount() + 1);
                } else {
                    deviceMapper.insert(device);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                }
            } catch (Exception e) {
                result.setFailCount(result.getFailCount() + 1);
                result.addError(0, device.getAssetNo(), device.getName(), e.getMessage());
            }
        }
    }

    // ==================== 列映射 ====================

    /**
     * CSV/XLSX 列 → Device 字段映射
     */
    private Device mapColumns(List<String> cols, Long userId, String batchId) {
        String assetNo = getCol(cols, 2);
        if (assetNo == null || assetNo.trim().isEmpty()) return null;

        Device d = new Device();
        d.setAssetNo(assetNo.trim());
        d.setName(getCol(cols, 3));
        d.setModel(getCol(cols, 4));
        d.setSpecs(getCol(cols, 5));
        d.setTotalQty(parseIntSafe(getCol(cols, 6), 1));
        d.setUnitPrice(parseBigDecimalSafe(getCol(cols, 7)));
        d.setTotalAmount(parseBigDecimalSafe(getCol(cols, 8)));
        d.setPurchaseDate(parseExcelDate(getCol(cols, 9)));
        d.setDepartment(getCol(cols, 10));
        d.setCustodian(getCol(cols, 11));
        d.setLocation(getCol(cols, 13));
        d.setDescription(getCol(cols, 14));
        d.setEduCategoryName(getCol(cols, 20));
        d.setEduCategoryCode(getCol(cols, 21));
        d.setGbCategoryName(getCol(cols, 22));
        d.setGbCategoryCode(getCol(cols, 23));
        d.setManufacturer(getCol(cols, 25));
        d.setSupplier(getCol(cols, 32));
        d.setInvoiceNo(getCol(cols, 33));
        d.setContractNo(getCol(cols, 34));
        d.setWarrantyPeriod(parseIntSafe(getCol(cols, 37), null));
        d.setImportBatchId(batchId);
        d.setStatus(1);
        d.setCreateBy(userId);
        return d;
    }

    // ==================== CSV 解析 ====================

    /**
     * 解析一行 CSV（处理引号内的逗号）
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
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
        return result;
    }

    // ==================== 类型转换帮助方法 ====================

    private String getCol(List<String> cols, int index) {
        if (index < cols.size()) {
            String val = cols.get(index);
            return (val != null && !val.trim().isEmpty()) ? val.trim() : null;
        }
        return null;
    }

    private Integer parseIntSafe(String val, Integer defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val.replace(",", "").replace("，", ""));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private BigDecimal parseBigDecimalSafe(String val) {
        if (val == null) return null;
        try {
            String clean = val.replace(",", "").replace("，", "").replace("\"", "");
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 Excel 序列号日期 → LocalDate
     * 支持格式：纯数字（Excel序列号）或 yyyy-MM-dd / yyyy/MM/dd
     */
    private LocalDate parseExcelDate(String val) {
        if (val == null || val.isEmpty()) return null;
        try {
            // 尝试解析为纯数字 → Excel 序列号
            int serial = Integer.parseInt(val.replace(",", "").trim());
            return EXCEL_EPOCH.plusDays(serial);
        } catch (NumberFormatException ignored) {
            // 尝试日期字符串
            try {
                return LocalDate.parse(val, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException ignored2) {
                try {
                    return LocalDate.parse(val, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                } catch (DateTimeParseException ignored3) {
                    return null;
                }
            }
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // 返回 Excel 序列号数字，后续由 parseExcelDate 统一解析
                    return String.valueOf(cell.getNumericCellValue());
                }
                // 避免科学计数法
                double dv = cell.getNumericCellValue();
                if (dv == Math.floor(dv) && !Double.isInfinite(dv)) {
                    return String.valueOf((long) dv);
                }
                return new BigDecimal(dv).toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}
