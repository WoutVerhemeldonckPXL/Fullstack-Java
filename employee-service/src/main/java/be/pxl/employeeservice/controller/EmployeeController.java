package be.pxl.employeeservice.controller;

import be.pxl.employeeservice.domain.dto.EmployeeRequest;
import be.pxl.employeeservice.domain.dto.EmployeeResponse;
import be.pxl.employeeservice.services.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class EmployeeController {
    private final EmployeeService employeeService;

    public EmployeeController (EmployeeService employeeService){
        this.employeeService = employeeService;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> addEmployee (@RequestBody @Valid EmployeeRequest employeeRequest){
        EmployeeResponse response = employeeService.addEmployee(employeeRequest);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> findEmployeeById (long id){
        EmployeeResponse employeeResponse = employeeService.findEmployeeById(id);
        return ResponseEntity.ok(employeeResponse);
    }

    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> findAllEmployees (){
        return ResponseEntity.ok().body(employeeService.getAllEmployees());
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<EmployeeResponse> findEmployeeByDepartment (@PathVariable long departmentId){
        return ResponseEntity.ok().body(employeeService.findEmployeeByDepartmentId(departmentId));
    }

    @GetMapping("/organization/organizationId")
    public ResponseEntity<EmployeeResponse> findEmployeeByOrganization(@PathVariable long oranizationoId){
        return ResponseEntity.ok().body(employeeService.findEmployeeByOrganizationId(oranizationoId));
    }

}
