package com.company.hrm.employee.service;

import com.company.hrm.common.exception.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DocumentValidator {
    private DocumentValidator() { }
    public static String validate(String name, byte[] data) {
        if (name==null || name.isBlank() || name.length()>255 || name.contains("/") || name.contains("\\")
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c)==Character.FORMAT))
            throw invalid();
        int dot=name.indexOf('.');
        if (dot<=0 || dot!=name.lastIndexOf('.')) throw invalid();
        String extension=name.substring(dot+1).toLowerCase(Locale.ROOT);
        String mime;
        if (starts(data, "%PDF-".getBytes(StandardCharsets.US_ASCII))) mime="application/pdf";
        else if (starts(data,new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff})) mime="image/jpeg";
        else if (starts(data,new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a})) mime="image/png";
        else throw invalid();
        boolean matches=switch(mime) {
            case "application/pdf" -> extension.equals("pdf");
            case "image/jpeg" -> Set.of("jpg","jpeg").contains(extension);
            default -> extension.equals("png");
        };
        if (!matches) throw invalid();
        return mime;
    }
    private static boolean starts(byte[] data, byte[] prefix) {
        return data.length>=prefix.length && Arrays.equals(data,0,prefix.length,prefix,0,prefix.length);
    }
    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.DOCUMENT_INVALID_FILE, "Tên hoặc định dạng tài liệu không hợp lệ; chỉ nhận PDF, JPEG, PNG");
    }
}
