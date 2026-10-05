package be.pxl.departmentservice.controller;

import be.pxl.departmentservice.domein.Department;
import be.pxl.departmentservice.domein.dto.DepartmentRequest;
import be.pxl.departmentservice.domein.dto.DepartmentResponse;
import be.pxl.departmentservice.services.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/department")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController (DepartmentService departmentService){
        this.departmentService = departmentService;
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> addEmployee (@RequestBody @Valid DepartmentRequest departmentRequest){
        DepartmentResponse response = departmentService.addDepartment(departmentRequest);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentResponse> findEmployeeById (@PathVariable Long id){
        DepartmentResponse employeeResponse = departmentService.findDepartmentById(id);
        return ResponseEntity.ok(employeeResponse);
    }

    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> findAllEmployees (){
        return ResponseEntity.ok().body(departmentService.getAllDepartments());
    }

    @GetMapping("/organisation/{organisationId}")
    public ResponseEntity<DepartmentResponse> findEmployeeByDepartment (@PathVariable Long organisationId){
        return ResponseEntity.ok().body(departmentService.findDepartmentByOrganisationId(organisationId));
    }
}
