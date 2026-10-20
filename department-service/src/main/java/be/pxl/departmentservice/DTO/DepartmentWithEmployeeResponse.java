package be.pxl.departmentservice.DTO;

import be.pxl.departmentservice.domein.dto.DepartmentResponse;

import java.util.ArrayList;

public record DepartmentWithEmployeeResponse(DepartmentResponse departmentResponse, ArrayList<EmployeeResponse> employees) {
}
