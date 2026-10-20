package be.pxl.organizationservice.DTO;

import java.util.ArrayList;

public record OrganizationWithDepRes (String name, ArrayList<DepartmentResponse> departmentResponses){}