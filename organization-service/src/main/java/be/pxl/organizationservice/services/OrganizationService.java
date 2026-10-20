package be.pxl.organizationservice.services;

import be.pxl.organizationservice.DTO.OrganizationWithDepRes;
import be.pxl.organizationservice.clients.DepartmentClient;
import be.pxl.organizationservice.clients.EmployeeClient;
import org.springframework.stereotype.Service;

@Service
public class OrganizationService {
    private final EmployeeClient employeeClient;
    private final DepartmentClient departmentClient;

    public OrganizationService(EmployeeClient employeeClient, DepartmentClient departmentClient){
        this.departmentClient = departmentClient;
        this.employeeClient = employeeClient;

    }

    public OrganizationWithDepRes getOrganizationWithDep (Long id) {

    }
}
