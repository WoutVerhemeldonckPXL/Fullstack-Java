package be.pxl.organizationservice.clients;

import be.pxl.organizationservice.DTO.DepartmentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;

@FeignClient(name = "department-service")
public interface DepartmentClient {
    @GetMapping("/department/organisation/{organisationId}")
    ArrayList<DepartmentResponse> getDepartments(Long id);
}
