package cn.weidong.llm.aiknowengine.document.util;

import cn.weidong.llm.aiknowengine.document.constant.FileType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeUtilTest {

    @Test
    void shouldReturnTrueWhenExtensionMatches() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unknown.bin",
                "application/octet-stream",
                new byte[]{1, 2, 3}
        );

        assertThat(FileTypeUtil.isFileType("report.pdf", file, FileType.PDF)).isTrue();
    }

    @Test
    void shouldReturnTrueWhenContentMatches() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unknown",
                "application/octet-stream",
                "%PDF-1.4\n%test".getBytes()
        );

        assertThat(FileTypeUtil.isFileType("unknown", file, FileType.PDF)).isTrue();
        assertThat(FileTypeUtil.getFileType("unknown", file)).isEqualTo(FileType.PDF);
    }

    @Test
    void shouldReturnFalseWhenExtensionAndContentDoNotMatch() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unknown.bin",
                "application/octet-stream",
                new byte[]{1, 2, 3}
        );

        assertThat(FileTypeUtil.isSupportedFileType("unknown.bin", file)).isFalse();
        assertThat(FileTypeUtil.isFileType("unknown.bin", file, FileType.PDF)).isFalse();
    }
}
