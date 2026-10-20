package be.pxl.departmentservice.client;

import be.pxl.departmentservice.DTO.EmployeeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;

@FeignClient(name = "employee-service")
public interface EmployeeClient {

    @GetMapping("/employee/organization/organizationId")
    ArrayList<EmployeeResponse> getEmployeesWithOrgId(Long id);
}
