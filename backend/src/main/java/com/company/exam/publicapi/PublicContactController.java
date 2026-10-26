package com.company.exam.publicapi;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.security.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public landing-page contact form ({@code specs/05 §73.2}; ADR-0037). No authentication.
 */
@RestController
@RequestMapping(path = "/api/v1/public/contact", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Public - Contact", description = "Landing page contact form")
public class PublicContactController {

    private final PublicContactService contactService;
    private final ClientIpResolver clientIpResolver;

    public PublicContactController(PublicContactService contactService, ClientIpResolver clientIpResolver) {
        this.contactService = contactService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Submit a contact request", description = "Public, rate-limited, and never blocks on delivery.")
    @PostMapping
    public ApiResponse<Void> contact(@Valid @RequestBody PublicContactDtos.ContactRequest request,
            HttpServletRequest httpRequest) {
        contactService.submit(request, clientIpResolver.resolve(httpRequest));
        return ApiResponse.ok("Thanks — we'll be in touch.", null);
    }
}
