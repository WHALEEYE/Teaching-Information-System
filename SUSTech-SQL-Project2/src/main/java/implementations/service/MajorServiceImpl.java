package implementations.service;

import cn.edu.sustech.cs307.database.SQLDataSource;
import cn.edu.sustech.cs307.dto.Department;
import cn.edu.sustech.cs307.dto.Major;
import cn.edu.sustech.cs307.exception.EntityNotFoundException;
import cn.edu.sustech.cs307.exception.IntegrityViolationException;
import cn.edu.sustech.cs307.service.MajorService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class MajorServiceImpl implements MajorService {

  private final SQLDataSource sds;

  public MajorServiceImpl() {
    sds = SQLDataSource.getInstance();
  }

  @Override
  public int addMajor(String name, int departmentId) {
    int id = -1;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn
          .prepareStatement("insert into major (name, department_id) values (?, ?) returning id");
      pStmt.setString(1, name);
      pStmt.setInt(2, departmentId);
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
  public void removeMajor(int majorId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("delete from major where id = ?");
      pStmt.setInt(1, majorId);
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
  public List<Major> getAllMajors() {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    List<Major> listOfMajors = new ArrayList<>();
    Major tempMajor;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select m.id, m.name, m.department_id, d.name from department d join major m on d.id = m.department_id");
      rst = pStmt.executeQuery();
      while (rst.next()) {
        tempMajor = new Major();
        tempMajor.department = new Department();
        tempMajor.id = rst.getInt(1);
        tempMajor.name = rst.getString(2);
        tempMajor.department.id = rst.getInt(3);
        tempMajor.department.name = rst.getString(4);
        listOfMajors.add(tempMajor);
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
    return listOfMajors;
  }

  @Override
  public Major getMajor(int majorId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    Major tempMajor = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select x.name, d.id, d.name from (select department_id, name from major where id = ?) x join department d on x.department_id = d.id");
      pStmt.setInt(1, majorId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        tempMajor = new Major();
        tempMajor.department = new Department();
        tempMajor.id = majorId;
        tempMajor.name = rst.getString(1);
        tempMajor.department.id = rst.getInt(2);
        tempMajor.department.name = rst.getString(3);
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
    return tempMajor;
  }

  @Override
  public void addMajorCompulsoryCourse(int majorId, String courseId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "insert into major_course_relations (major_id, course_id, is_compulsory) values (?, ?, true)");
      pStmt.setInt(1, majorId);
      pStmt.setString(2, courseId);
      pStmt.execute();
      pStmt.close();
      conn.close();
    } catch (SQLException e) {
      throw new IntegrityViolationException();
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
  public void addMajorElectiveCourse(int majorId, String courseId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("insert into major_course_relations (major_id, course_id, is_compulsory) values (?, ?, false)");
      pStmt.setInt(1, majorId);
      pStmt.setString(2, courseId);
      pStmt.execute();
      pStmt.close();
      conn.close();
    } catch (SQLException e) {
      throw new IntegrityViolationException();
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
}
