package be.pxl.employeeservice.repository;

import be.pxl.employeeservice.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByDepartmentId(long departmentId);

    Optional<Employee> findByOrganizationId(long organizationId);
}
