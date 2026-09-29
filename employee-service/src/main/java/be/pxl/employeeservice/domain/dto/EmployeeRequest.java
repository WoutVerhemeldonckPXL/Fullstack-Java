package be.pxl.employeeservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record EmployeeRequest (@NotBlank String firstname, @NotBlank String lastname, @NotBlank String email, @NotEmpty long departmentId, @NotEmpty long organizationId){
}
