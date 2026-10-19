package org.una.programmingIII.UTEMP_Project.controllers.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManualReviewRequest {
    @NotBlank(message = "Comments must not be blank")
    @Size(max = 1000, message = "Comments must be at most 1000 characters long")
    private String comments;
}
