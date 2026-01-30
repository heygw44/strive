package io.heygw44.strive.support;

import io.heygw44.strive.global.exception.BusinessException;
import io.heygw44.strive.global.exception.ErrorCode;
import io.heygw44.strive.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }

    @PostMapping("/validation")
    public ApiResponse<String> validation(@Valid @RequestBody TestRequest request) {
        return ApiResponse.success("ok");
    }

    @GetMapping("/business")
    public ApiResponse<String> business() {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    public record TestRequest(@NotBlank String name) {}
}
