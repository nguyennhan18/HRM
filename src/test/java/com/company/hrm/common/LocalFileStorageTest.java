package com.company.hrm.common;
import com.company.hrm.common.storage.LocalFileStorage;
import com.company.hrm.common.exception.BusinessException;
import com.company.hrm.employee.service.DocumentValidator;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class LocalFileStorageTest {
    @TempDir Path temporary;
    LocalFileStorage storage() throws Exception { return new LocalFileStorage(temporary.toRealPath().toString(),"10MB"); }
    @Test void should_reject_traversal_for_all_storage_operations() throws Exception {
        var storage=storage();
        for(String name:new String[]{"../secret","/etc/passwd","..\\secret","a/b", ""}) {
            assertThatThrownBy(()->storage.read(name)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->storage.delete(name)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void should_reject_symlink_files_and_directories() throws Exception {
        var storage=storage(); Path outside=Files.writeString(temporary.resolve("outside"),"secret");
        String name=UUID.randomUUID().toString(); Files.createSymbolicLink(temporary.resolve(name),outside);
        assertThatThrownBy(()->storage.read(name)).isInstanceOf(BusinessException.class);
        Path link=temporary.resolve("link"); Files.createSymbolicLink(link,temporary.toRealPath());
        var linked=new LocalFileStorage(link.toString(),"10MB");
        assertThatThrownBy(()->linked.store(new byte[]{1})).isInstanceOf(BusinessException.class);
        assertThat(Files.readString(outside)).isEqualTo("secret");
    }
    @Test void should_roundtrip_unique_files_and_delete_only_selected_file() throws Exception {
        var storage=storage(); var bytes=new byte[]{1,2,3};
        String first=storage.store(bytes),second=storage.store(bytes);
        assertThat(first).isNotEqualTo(second);
        assertThat(storage.read(first)).isEqualTo(bytes);
        storage.delete(first); assertThat(storage.read(second)).isEqualTo(bytes);
    }
    @Test void should_detect_allowed_magic_bytes_independent_of_browser_mime() {
        assertThat(DocumentValidator.validate("ảnh.PNG",new byte[]{(byte)0x89,80,78,71,13,10,26,10})).isEqualTo("image/png");
        assertThat(DocumentValidator.validate("ảnh.jpeg",new byte[]{(byte)255,(byte)216,(byte)255})).isEqualTo("image/jpeg");
        assertThatThrownBy(()->DocumentValidator.validate("file.pdf",new byte[]{'P','K',3,4})).isInstanceOf(BusinessException.class);
    }
    @Test void should_reject_invalid_storage_limits_and_empty_files() throws Exception {
        assertThatThrownBy(()->new LocalFileStorage(temporary.toString(),"11MB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new LocalFileStorage(temporary.toString(),"0B")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->storage().store(new byte[0])).isInstanceOf(BusinessException.class);
    }
}
