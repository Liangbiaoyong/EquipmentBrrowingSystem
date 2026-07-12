package com.gzhu.equipment.common;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel XLSX 导出工具 — 通用表格数据导出
 * Apache POI 5.2.5
 */
public class ExcelExportUtil {

    /** 将 Map 列表导出为 XLSX 字节数组 */
    public static byte[] exportToXlsx(List<Map<String, Object>> rows, LinkedHashMap<String, String> headers) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("数据");

            // 表头样式
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 表头行
            Row headerRow = sheet.createRow(0);
            int colIdx = 0;
            for (String label : headers.values()) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(label);
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            int rowIdx = 1;
            for (Map<String, Object> row : rows) {
                Row dataRow = sheet.createRow(rowIdx++);
                int c = 0;
                for (String key : headers.keySet()) {
                    Cell cell = dataRow.createCell(c++);
                    Object val = row.get(key);
                    if (val == null) {
                        cell.setCellValue("");
                    } else if (val instanceof Number) {
                        cell.setCellValue(((Number) val).doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(val));
                    }
                }
            }

            // 自动调整列宽
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                if (width > 15000) sheet.setColumnWidth(i, 15000);
                else if (width < 3000) sheet.setColumnWidth(i, 3000);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("XLSX导出失败: " + e.getMessage(), e);
        }
    }
}
