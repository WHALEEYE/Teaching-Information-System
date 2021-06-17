package implementations.service;

import cn.edu.sustech.cs307.database.SQLDataSource;
import cn.edu.sustech.cs307.dto.Semester;
import cn.edu.sustech.cs307.exception.EntityNotFoundException;
import cn.edu.sustech.cs307.exception.IntegrityViolationException;
import cn.edu.sustech.cs307.service.SemesterService;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class SemesterServiceImpl implements SemesterService {

  private final SQLDataSource sds;

  public SemesterServiceImpl() {
    sds = SQLDataSource.getInstance();
  }

  @Override
  public int addSemester(String name, Date begin, Date end) {
    int id = -1;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    if (begin.after(end)) {
      throw new IllegalArgumentException();
    }
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "insert into semester (name, begin_date, end_date) values (?, ?, ?) returning id");
      pStmt.setString(1, name);
      pStmt.setDate(2, begin);
      pStmt.setDate(3, end);
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
  public void removeSemester(int semesterId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("delete from semester where id = ?");
      pStmt.setInt(1, semesterId);
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
  public List<Semester> getAllSemesters() {
    Semester tempSemester;
    List<Semester> listOfSemesters = new ArrayList<>();
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select id, name, begin_date, end_date from semester");
      rst = pStmt.executeQuery();
      while (rst.next()) {
        tempSemester = new Semester();
        tempSemester.id = rst.getInt(1);
        tempSemester.name = rst.getString(2);
        tempSemester.begin = rst.getDate(3);
        tempSemester.end = rst.getDate(4);
        listOfSemesters.add(tempSemester);
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
    return listOfSemesters;
  }

  @Override
  public Semester getSemester(int semesterId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    Semester tempSemester = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn
          .prepareStatement("select id, name, begin_date, end_date from semester where id = ?");
      pStmt.setInt(1, semesterId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        tempSemester = new Semester();
        tempSemester.id = rst.getInt(1);
        tempSemester.name = rst.getString(2);
        tempSemester.begin = rst.getDate(3);
        tempSemester.end = rst.getDate(4);
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
    return tempSemester;
  }
}
