package cn.weidong.llm.aiknowengine.document.util;

import cn.weidong.llm.aiknowengine.document.constant.FileType;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * 文件类型识别工具。
 */
public final class FileTypeUtil {

    private static final Logger log = LoggerFactory.getLogger(FileTypeUtil.class);
    private static final Tika TIKA = new Tika();

    /** 后缀名到业务文件类型的映射。 */
    private static final Map<String, FileType> EXTENSION_TYPE_MAPPING = Map.ofEntries(
            Map.entry("pdf", FileType.PDF),
            Map.entry("doc", FileType.DOC),
            Map.entry("docx", FileType.DOC),
            Map.entry("txt", FileType.TXT),
            Map.entry("html", FileType.HTML),
            Map.entry("htm", FileType.HTML),
            Map.entry("md", FileType.MARKDOWN),
            Map.entry("markdown", FileType.MARKDOWN),
            Map.entry("csv", FileType.CSV),
            Map.entry("xls", FileType.EXCEL),
            Map.entry("xlsx", FileType.EXCEL)
    );

    /** Tika 识别出的 MIME 类型到业务文件类型的映射。 */
    private static final Map<String, FileType> MEDIA_TYPE_MAPPING = Map.ofEntries(
            Map.entry("application/pdf", FileType.PDF),
            Map.entry("application/msword", FileType.DOC),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", FileType.DOC),
            Map.entry("text/plain", FileType.TXT),
            Map.entry("text/html", FileType.HTML),
            Map.entry("text/markdown", FileType.MARKDOWN),
            Map.entry("text/x-web-markdown", FileType.MARKDOWN),
            Map.entry("text/csv", FileType.CSV),
            Map.entry("application/vnd.ms-excel", FileType.EXCEL),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FileType.EXCEL)
    );

    private FileTypeUtil() {
    }

    /**
     * 根据文件名和上传文件信息识别文件类型，无法识别时返回 null。
     *
     * @param file 上传文件
     * @return 文件类型
     */
    public static FileType getFileType(MultipartFile file) {
        FileType fileType = getFileTypeByFileName(file.getOriginalFilename());
        if (fileType != null) {
            log.debug("File type detected by extension, fileName={}, fileType={}", file.getOriginalFilename(), fileType);
            return fileType;
        }
        FileType contentFileType = getFileTypeByContent(file);
        log.debug("File type detected by content, fileName={}, fileType={}", file.getOriginalFilename(), contentFileType);
        return contentFileType;
    }

    /**
     * 判断是否为支持的文件类型。文件后缀或文件内容任一可识别时返回 true。
     *
     * @param fileName 文件名
     * @param file 上传文件
     * @return 是否支持
     */
    public static boolean isSupportedFileType(String fileName, MultipartFile file) {
        FileType extensionType = getFileTypeByFileName(resolveFileName(fileName, file));
        FileType contentType = getFileTypeByContent(file);
        boolean supported = extensionType != null || contentType != null;
        log.info("Supported file type checked, fileName={}, extensionType={}, contentType={}, supported={}",
                fileName, extensionType, contentType, supported);
        return supported;
    }

    /**
     * 判断是否为指定文件类型。文件后缀或文件内容任一匹配目标类型时返回 true。
     *
     * @param fileName 文件名
     * @param file 上传文件
     * @param expectedType 目标文件类型
     * @return 是否匹配
     */
    public static boolean isFileType(String fileName, MultipartFile file, FileType expectedType) {
        if (expectedType == null) {
            return false;
        }
        FileType extensionType = getFileTypeByFileName(resolveFileName(fileName, file));
        FileType contentType = getFileTypeByContent(file);
        boolean matched = expectedType == extensionType || expectedType == contentType;
        log.info("Expected file type checked, fileName={}, expectedType={}, extensionType={}, contentType={}, matched={}",
                fileName, expectedType, extensionType, contentType, matched);
        return matched;
    }

    private static String resolveFileName(String fileName, MultipartFile file) {
        if (StringUtils.hasText(fileName)) {
            return fileName;
        }
        if (file != null && StringUtils.hasText(file.getOriginalFilename())) {
            return file.getOriginalFilename();
        }
        return null;
    }

    private static FileType getFileTypeByFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String extension = StringUtils.getFilenameExtension(fileName);
        if (!StringUtils.hasText(extension)) {
            return null;
        }
        return EXTENSION_TYPE_MAPPING.get(extension.toLowerCase(Locale.ROOT));
    }

    private static FileType getFileTypeByContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String mediaType = TIKA.detect(file.getInputStream(), file.getOriginalFilename());
            if (!StringUtils.hasText(mediaType)) {
                log.debug("Tika returned empty media type, originalFilename={}", file.getOriginalFilename());
                return null;
            }
            FileType fileType = MEDIA_TYPE_MAPPING.get(mediaType.toLowerCase(Locale.ROOT));
            log.debug("Tika media type detected, originalFilename={}, mediaType={}, fileType={}",
                    file.getOriginalFilename(), mediaType, fileType);
            return fileType;
        } catch (IOException ex) {
            log.warn("Failed to detect file type by content, originalFilename={}", file.getOriginalFilename(), ex);
            return null;
        }
    }
}
