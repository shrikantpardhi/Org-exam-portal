package com.company.exam.testfixtures;

import com.company.exam.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint used to prove the 403 (authenticated but not permitted) behaviour.
 *
 * <p>It is a deliberate test fixture, not a product endpoint: real authorization belongs to the
 * Identity and feature modules. It lives in the test sources so it is never packaged.
 */
@RestController
@RequestMapping("/api/v1/test-support")
public class TestAuthorityController {

    @GetMapping("/admin-only")
    @PreAuthorize("hasAuthority('TEST_ADMIN')")
    public ApiResponse<String> adminOnly() {
        return ApiResponse.ok("granted");
    }
}
