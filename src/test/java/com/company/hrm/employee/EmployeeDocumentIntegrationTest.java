package com.company.hrm.employee;

import com.company.hrm.auth.entity.User;
import com.company.hrm.auth.repository.*;
import com.company.hrm.common.exception.*;
import com.company.hrm.employee.entity.DocumentType;
import com.company.hrm.employee.service.*;
import com.company.hrm.employee.repository.EmployeeDocumentRepository;
import com.company.hrm.testsupport.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @AutoConfigureMockMvc @WithMockUser(roles="HR")
class EmployeeDocumentIntegrationTest extends AbstractIntegrationTest {
    static final byte[] PDF="%PDF-1.7\nSynthetic test document\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static final Path DIRECTORY=directory();
    static Path directory() {
        try { return Files.createTempDirectory("hrm-documents-").toRealPath(); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @Autowired EmployeeDocumentService documents;
    @Autowired EmployeeDocumentRepository repository;
    @Autowired EmployeeService employees;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired com.company.hrm.auth.service.AuthService auth;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        postgres.start();
        r.add("spring.datasource.url",postgres::getJdbcUrl);
        r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        r.add("hrm.documents.directory",DIRECTORY::toString);
    }
    @AfterAll static void cleanup() throws Exception {
        try(var paths=Files.walk(DIRECTORY)) { for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
    UUID employee() { return employees.create(new EmployeeFixture().build(),null).id(); }
    MockMultipartFile file(String name,byte[] data) { return new MockMultipartFile("file",name,"text/plain",data); }
    String path(UUID employee) { return "/api/v1/employees/"+employee+"/documents"; }
    static void hr() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        "hr","unused",AuthorityUtils.createAuthorityList("ROLE_HR"))); }
    void error(Runnable action,ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(code));
    }
    @Test void should_store_actual_mime_checksum_random_name_and_audit_when_uploading() throws Exception {
        UUID id=employee();
        var response=documents.upload(id,DocumentType.CV,file("Hồ sơ.pdf",PDF),"document-test");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.checksum()).isEqualTo(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(PDF)));
        var stored=repository.findById(response.id()).orElseThrow();
        assertThat(stored.getStoredName()).matches("[a-f0-9-]{36}").doesNotContain("Hồ sơ");
        assertThat(Files.readAllBytes(DIRECTORY.resolve(stored.getStoredName()))).isEqualTo(PDF);
        mvc.perform(get(path(id)+"/"+response.id()+"/download")).andExpect(status().isOk())
            .andExpect(content().bytes(PDF)).andExpect(header().string("Content-Type","application/pdf"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(result -> {
                var disposition=org.springframework.http.ContentDisposition.parse(result.getResponse().getHeader("Content-Disposition"));
                assertThat(disposition.getType()).isEqualTo("attachment");
                assertThat(disposition.getFilename()).isEqualTo("Hồ sơ.pdf");
            });
        String audit=jdbc.queryForObject("select after_json::text from audit_logs where entity_id=?",String.class,response.id());
        assertThat(audit).contains("CV").doesNotContain("Hồ sơ", "%PDF",stored.getStoredName());
    }
    @ParameterizedTest @ValueSource(strings={"../cv.pdf","..\\cv.pdf","/cv.pdf","cv.exe.pdf","cv.pdf.exe","cv\r\n.pdf","cv\u0000.pdf","cv\u202e.pdf",".pdf"})
    void should_reject_unsafe_names_when_uploading(String name) {
        UUID id=employee(); error(()->documents.upload(id,DocumentType.CV,file(name,PDF),null),ErrorCode.DOCUMENT_INVALID_FILE);
    }
    @Test void should_reject_empty_disallowed_and_disguised_content() {
        UUID id=employee();
        for(var f:List.of(file("cv.pdf",new byte[0]),file("cv.pdf","<html>bad</html>".getBytes()),file("cv.png",PDF)))
            error(()->documents.upload(id,DocumentType.CV,f,null),ErrorCode.DOCUMENT_INVALID_FILE);
    }
    @Test void should_enforce_limit_in_service_and_http_when_size_exceeds_ten_mib() throws Exception {
        UUID id=employee(); byte[] big=new byte[10*1024*1024+1]; System.arraycopy(PDF,0,big,0,PDF.length);
        error(()->documents.upload(id,DocumentType.CV,file("cv.pdf",big),null),ErrorCode.DOCUMENT_TOO_LARGE);
        mvc.perform(multipart(path(id)).file(file("cv.pdf",big)).param("type","CV"))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("code").value("DOCUMENT_TOO_LARGE"));
        hr(); byte[] exact=Arrays.copyOf(big,10*1024*1024);
        assertThat(documents.upload(id,DocumentType.CV,file("cv.pdf",exact),null).size()).isEqualTo(exact.length);
    }
    @Test void should_enforce_multipart_limit_in_real_servlet_container() {
        UUID id=employee();
        String email=EmployeeFixture.email(), password=UUID.randomUUID().toString();
        users.saveAndFlush(new User(email,passwords.encode(password),roles.findByCode("HR").orElseThrow()));
        String token=auth.login(new com.company.hrm.auth.dto.AuthDtos.Login(email,password),"document-http-test").accessToken();
        var headers=new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token); headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        for(int size:new int[]{10*1024*1024,10*1024*1024+1}) {
            byte[] bytes=Arrays.copyOf(PDF,size);
            var body=new org.springframework.util.LinkedMultiValueMap<String,Object>();
            body.add("type","CV");
            body.add("file",new org.springframework.core.io.ByteArrayResource(bytes) {
                @Override public String getFilename() { return "cv.pdf"; }
            });
            var result=http.postForEntity(path(id),new org.springframework.http.HttpEntity<>(body,headers),String.class);
            assertThat(result.getStatusCode().value()).isEqualTo(size==10*1024*1024?201:413);
            if(size>10*1024*1024) assertThat(result.getBody()).contains("DOCUMENT_TOO_LARGE");
        }
    }
    @Test void should_return_validation_and_not_found_errors() throws Exception {
        UUID id=employee();
        mvc.perform(multipart(path(id)).file(file("cv.pdf",PDF)).param("type","INVALID")).andExpect(status().isBadRequest());
        mvc.perform(multipart(path(id)).param("type","CV").with(user("hr").roles("HR"))).andExpect(status().isBadRequest());
        mvc.perform(multipart(path(UUID.randomUUID())).file(file("cv.pdf",PDF)).param("type","CV").with(user("hr").roles("HR"))).andExpect(status().isNotFound());
        mvc.perform(get(path(id)+"/"+UUID.randomUUID()+"/download").with(user("hr").roles("HR"))).andExpect(status().isNotFound());
    }
    @Test void should_enforce_both_endpoint_and_service_permissions() throws Exception {
        UUID id=employee(); UUID other=employee();
        UUID userId=users.saveAndFlush(new User(EmployeeFixture.email(),"unused",roles.findByCode("EMPLOYEE").orElseThrow())).getId();
        jdbc.update("update employees set user_id=? where id=?",userId,id);
        var doc=documents.upload(id,DocumentType.CV,file("cv.pdf",PDF),null);
        var otherDoc=documents.upload(other,DocumentType.CV,file("cv.pdf",PDF),null);
        String download=path(id)+"/"+doc.id()+"/download";
        mvc.perform(multipart(path(id)).file(file("cv.pdf",PDF)).param("type","CV").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(download).with(anonymous())).andExpect(status().isUnauthorized());
        for(String role:List.of("EMPLOYEE","MANAGER")) {
            mvc.perform(multipart(path(id)).file(file("cv.pdf",PDF)).param("type","CV").with(user(userId.toString()).roles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get(download).with(user(userId.toString()).roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get(download).with(user(userId.toString()).roles("EMPLOYEE"))).andExpect(status().isOk());
        mvc.perform(get(path(other)+"/"+otherDoc.id()+"/download").with(user(userId.toString()).roles("EMPLOYEE"))).andExpect(status().isForbidden());
        mvc.perform(get(path(id)+"/"+otherDoc.id()+"/download").with(user(userId.toString()).roles("EMPLOYEE"))).andExpect(status().isNotFound());
        for(String role:List.of("ADMIN","HR")) {
            mvc.perform(get(download).with(user("staff").roles(role))).andExpect(status().isOk());
            mvc.perform(multipart(path(id)).file(file("cv.pdf",PDF)).param("type","CV").with(user("staff").roles(role)))
                .andExpect(status().isCreated()).andExpect(jsonPath("storedName").doesNotExist());
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId.toString(),"unused",AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE")));
        assertThatThrownBy(()->documents.upload(id,DocumentType.CV,file("cv.pdf",PDF),null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->documents.download(other,otherDoc.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(documents.download(id,doc.id()).content()).isEqualTo(PDF);
    }
    @Test void should_enforce_employee_status_and_soft_deletion() throws Exception {
        UUID id=employee();
        var doc=documents.upload(id,DocumentType.CV,file("cv.pdf",PDF),null);
        for(String state:List.of("ACTIVE","ON_LEAVE_LONG")) {
            jdbc.update("update employees set status=? where id=?",state,id);
            documents.upload(id,DocumentType.OTHER,file("cv.pdf",PDF),null);
        }
        jdbc.update("update employees set status='TERMINATED' where id=?",id);
        error(()->documents.upload(id,DocumentType.CV,file("cv.pdf",PDF),null),ErrorCode.DOCUMENT_EMPLOYEE_INELIGIBLE);
        assertThat(documents.download(id,doc.id()).content()).isEqualTo(PDF);
        jdbc.update("update employees set deleted_at=current_timestamp where id=?",id);
        error(()->documents.upload(id,DocumentType.CV,file("cv.pdf",PDF),null),ErrorCode.HRM_RESOURCE_NOT_FOUND);
        mvc.perform(get(path(id)+"/"+doc.id()+"/download")).andExpect(status().isNotFound());
    }
    @Test void should_cleanup_disk_when_outer_transaction_rolls_back() throws Exception {
        UUID id=employee(); var tx=new TransactionTemplate(transactions);
        final UUID[] documentId={null}; final String[] name={null};
        tx.executeWithoutResult(status->{
            var doc=documents.upload(id,DocumentType.CV,file("cv.pdf",PDF),null);
            documentId[0]=doc.id(); name[0]=repository.findById(doc.id()).orElseThrow().getStoredName();
            assertThat(Files.exists(DIRECTORY.resolve(name[0]))).isTrue(); status.setRollbackOnly();
        });
        assertThat(repository.findById(documentId[0])).isEmpty();
        assertThat(Files.exists(DIRECTORY.resolve(name[0]))).isFalse();
    }
    @Test void should_refuse_tampered_or_missing_file_when_downloading() throws Exception {
        UUID id=employee(); var doc=documents.upload(id,DocumentType.CV,file("cv.pdf",PDF),null);
        Path path=DIRECTORY.resolve(repository.findById(doc.id()).orElseThrow().getStoredName());
        Files.writeString(path,"tampered");
        error(()->documents.download(id,doc.id()),ErrorCode.HRM_INTERNAL_ERROR);
        Files.delete(path);
        error(()->documents.download(id,doc.id()),ErrorCode.HRM_RESOURCE_NOT_FOUND);
    }
}
