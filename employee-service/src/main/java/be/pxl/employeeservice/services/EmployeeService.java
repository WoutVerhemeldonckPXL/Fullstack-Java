package be.pxl.employeeservice.services;

import be.pxl.employeeservice.domain.Employee;
import be.pxl.employeeservice.domain.dto.EmployeeRequest;
import be.pxl.employeeservice.domain.dto.EmployeeResponse;
import be.pxl.employeeservice.exception.ResourceNotFoundException;
import be.pxl.employeeservice.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository){
        this.employeeRepository = employeeRepository;
    }

    public EmployeeResponse addEmployee (EmployeeRequest employeeRequest){
        Employee employee = new Employee();
        employee.setFirstName(employeeRequest.firstname());
        employee.setLastName(employeeRequest.lastname());
        employee.setEmail(employeeRequest.email());
        employee.setDepartmentId(employeeRequest.departmentId());
        employee.setOrganizationId(employeeRequest.organizationId());

        employeeRepository.save(employee);
        return new EmployeeResponse(employee.getFirstName(), employee.getLastName(), employee.getEmail());
    }

    public EmployeeResponse findEmployeeById(long id){
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);

        if (optionalEmployee.isEmpty()){
            throw new ResourceNotFoundException("Deze werknemer met gegeven ID bestaat niet!");
        }
        Employee employee = optionalEmployee.get();

        return new EmployeeResponse (employee.getFirstName(), employee.getLastName(), employee.getEmail());
    }

    public List<EmployeeResponse> getAllEmployees (){
        List<EmployeeResponse> employees = employeeRepository.findAll().stream()
                .map(e -> new EmployeeResponse(e.getFirstName(), e.getLastName(), e.getEmail()))
                .toList();

        return employees;
    }

    public EmployeeResponse findEmployeeByDepartmentId (long departmentId){
        Optional<Employee> optionalEmployee = employeeRepository.findByDepartmentId(departmentId);

        if (optionalEmployee.isEmpty()){
            throw new ResourceNotFoundException("Deze employee kan niet gevonden worden met de gegeven derpartemnet ID!");
        }

        Employee employee = optionalEmployee.get();

        return new EmployeeResponse(employee.getFirstName(), employee.getLastName(), employee.getEmail());
    }

    public EmployeeResponse findEmployeeByOrganizationId (long organizationId){
        Optional<Employee> optionalEmployee = employeeRepository.findByOrganizationId(organizationId);

        if (optionalEmployee.isEmpty()){
            throw new ResourceNotFoundException("Deze employee kan niet gevonden worden met de gegeven organisatie ID!");
        }

        Employee employee = optionalEmployee.get();

        return new EmployeeResponse(employee.getFirstName(), employee.getLastName(), employee.getEmail());
    }
}
