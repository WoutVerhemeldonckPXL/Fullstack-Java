package be.pxl.departmentservice.services;

import be.pxl.departmentservice.DTO.DepartmentWithEmployeeResponse;
import be.pxl.departmentservice.DTO.EmployeeResponse;
import be.pxl.departmentservice.client.EmployeeClient;
import be.pxl.departmentservice.domein.Department;
import be.pxl.departmentservice.domein.dto.DepartmentRequest;
import be.pxl.departmentservice.domein.dto.DepartmentResponse;
import be.pxl.departmentservice.exception.ResourceNotFoundException;
import be.pxl.departmentservice.repositorry.DepartmentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final EmployeeClient employeeClient;

    public DepartmentService(DepartmentRepository departmentRepository, EmployeeClient employeeClient){
        this.departmentRepository = departmentRepository;
        this.employeeClient = employeeClient;
    }

    public DepartmentResponse addDepartment (DepartmentRequest departmentRequest){
        Department department = new Department();
        department.setDepartmentName(departmentRequest.name());
        department.setOrganisationId(departmentRequest.organisationId());

        departmentRepository.save(department);
        return new DepartmentResponse(department.getId(), department.getDepartmentName());
    }

    public DepartmentResponse findDepartmentById(Long id){
        Optional<Department> departmentOptional = departmentRepository.findById(id);

        if (departmentOptional.isEmpty()){
            throw new ResourceNotFoundException("Deze werknemer met gegeven ID bestaat niet!");
        }
        Department department = departmentOptional.get();

        return new DepartmentResponse(department.getId(), department.getDepartmentName());
    }

    public List<DepartmentResponse> getAllDepartments (){
        List<DepartmentResponse> departments = departmentRepository.findAll().stream()
                .map(e -> new DepartmentResponse(e.getId(), e.getDepartmentName()))
                .toList();

        return departments;
    }

    public DepartmentResponse findDepartmentByOrganisationId (Long organisationId){
        Optional<Department> optionalDepartment = departmentRepository.findByOrganisationId(organisationId);

        if (optionalDepartment.isEmpty()){
            throw new ResourceNotFoundException("Deze employee kan niet gevonden worden met de gegeven derpartemnet ID!");
        }

        Department department = optionalDepartment.get();

        return new DepartmentResponse(department.getId(), department.getDepartmentName());
    }

    public DepartmentWithEmployeeResponse findDepartmentByOrganisationIdWithEmployees (Long organisationId){
        Optional<Department> optionalDepartment = departmentRepository.findByOrganisationId(organisationId);

        if (optionalDepartment.isEmpty()){
            throw new ResourceNotFoundException("Deze employee kan niet gevonden worden met de gegeven derpartemnet ID!");
        }

        ArrayList<EmployeeResponse> employees = employeeClient.getEmployeesWithOrgId(organisationId);

        Department department = optionalDepartment.get();

        return new DepartmentWithEmployeeResponse(new DepartmentResponse(department.getId(), department.getDepartmentName()), employees);
    }
}
