package org.una.programmingIII.UTEMP_Project.controllers.responses;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ApiResponse<T> {
    private T data;
    private int statusCode;
    private String message;

    public ApiResponse() {
        data = null;
        statusCode = 0;
        message = "";

    }

    public ApiResponse(T data) {
        this.data = data;
        this.statusCode = 200;
        this.message = "";
    }

    public ApiResponse(int statusCode, String errorMessage) {
        this.statusCode = statusCode;
        this.message = errorMessage;
        this.data = null;
    }

    public static <T> ApiResponse<T> fromException(int statusCode, String errorMessage) {
        return new ApiResponse<>(statusCode, errorMessage);
    }
}

