package com.parking;

import com.parking.db.AdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminRepository Tests")
class AdminRepositoryTest {

    @Test
    @DisplayName("Default admin credentials authenticate")
    void defaultAdmin_authenticates() {
        AdminRepository repo = new AdminRepository();

        AdminRepository.AdminRecord admin = repo.authenticate("admin", "admin123");

        assertNotNull(admin);
        assertEquals("admin", admin.username);
        assertEquals("ADMIN", admin.role);
    }

    @Test
    @DisplayName("Wrong admin password is rejected")
    void wrongPassword_rejected() {
        AdminRepository repo = new AdminRepository();

        assertNull(repo.authenticate("admin", "wrong-password"));
    }

    @Test
    @DisplayName("Password hashing is stable and not plain text")
    void hashPassword_stable() {
        String first = AdminRepository.hashPassword("admin123");
        String second = AdminRepository.hashPassword("admin123");

        assertEquals(first, second);
        assertNotEquals("admin123", first);
        assertEquals(64, first.length());
    }
}
