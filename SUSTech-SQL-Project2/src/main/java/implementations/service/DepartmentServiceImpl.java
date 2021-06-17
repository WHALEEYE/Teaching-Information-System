package implementations.service;

import cn.edu.sustech.cs307.database.SQLDataSource;
import cn.edu.sustech.cs307.dto.Department;
import cn.edu.sustech.cs307.exception.EntityNotFoundException;
import cn.edu.sustech.cs307.exception.IntegrityViolationException;
import cn.edu.sustech.cs307.service.DepartmentService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class DepartmentServiceImpl implements DepartmentService {

  private final SQLDataSource sds;

  public DepartmentServiceImpl() {
    sds = SQLDataSource.getInstance();
  }

  @Override
  public int addDepartment(String name) {
    int id = -1;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("insert into department (name) values (?) returning id");
      pStmt.setString(1, name);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        id = rst.getInt(1);
      }
    } catch (SQLException e) {
      throw new IntegrityViolationException();
    } finally {
      try {
        if (rst != null) {
          rst.close();
        }
        if (pStmt != null) {
          pStmt.close();
        }
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return id;
  }

  @Override
  public void removeDepartment(int departmentId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("delete from department where id = ?");
      pStmt.setInt(1, departmentId);
      pStmt.execute();
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        if (pStmt != null) {
          pStmt.close();
        }
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public List<Department> getAllDepartments() {
    Department tempDepartment;
    List<Department> listOfDepartments = new ArrayList<>();
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select id, name from department");
      rst = pStmt.executeQuery();
      while (rst.next()) {
        tempDepartment = new Department();
        tempDepartment.id = rst.getInt(1);
        tempDepartment.name = rst.getString(2);
        listOfDepartments.add(tempDepartment);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        if (rst != null) {
          rst.close();
        }
        if (pStmt != null) {
          pStmt.close();
        }
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return listOfDepartments;
  }

  @Override
  public Department getDepartment(int departmentId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    Department tempDepartment = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select name from department where id = ?");
      rst = pStmt.executeQuery();
      if (rst.next()) {
        tempDepartment = new Department();
        tempDepartment.id = departmentId;
        tempDepartment.name = rst.getString(1);
      } else {
        throw new EntityNotFoundException();
      }
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        if (rst != null) {
          rst.close();
        }
        if (pStmt != null) {
          pStmt.close();
        }
        if (conn != null) {
          conn.close();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return tempDepartment;
  }
}
