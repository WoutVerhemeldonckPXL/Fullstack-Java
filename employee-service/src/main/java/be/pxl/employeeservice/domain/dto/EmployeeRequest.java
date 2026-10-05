package be.pxl.employeeservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record EmployeeRequest (@NotBlank String firstname, @NotBlank String lastname, @NotBlank String email, @NotNull long departmentId, @NotNull long organizationId){
}
