package implementations.service;

import cn.edu.sustech.cs307.database.SQLDataSource;
import cn.edu.sustech.cs307.dto.CourseSection;
import cn.edu.sustech.cs307.exception.EntityNotFoundException;
import cn.edu.sustech.cs307.exception.IntegrityViolationException;
import cn.edu.sustech.cs307.service.InstructorService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class InstructorServiceImpl implements InstructorService {

  private final SQLDataSource sds;

  public InstructorServiceImpl() {
    sds = SQLDataSource.getInstance();
  }

  @Override
  public void addInstructor(int userId, String firstName, String lastName) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn
          .prepareStatement(
              "insert into instructor (id, first_name, last_name, full_name, other_name) values (?, ?, ?, default, default)");
      pStmt.setInt(1, userId);
      pStmt.setString(2, firstName);
      pStmt.setString(3, lastName);
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
  public List<CourseSection> getInstructedCourseSections(int instructorId, int semesterId) {
    CourseSection tempSection;
    List<CourseSection> listOfSections = new ArrayList<>();
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from get_instructed_course_sections(?, ?) as (sec_id int, sec_name varchar, total_capacity int, left_capacity int)");
      pStmt.setInt(1, instructorId);
      pStmt.setInt(2, semesterId);
      rst = pStmt.executeQuery();
      while (rst.next()) {
        tempSection = new CourseSection();
        tempSection.id = rst.getInt(1);
        if (rst.wasNull()) {
          throw new EntityNotFoundException();
        }
        tempSection.name = rst.getString(2);
        tempSection.totalCapacity = rst.getInt(3);
        tempSection.leftCapacity = rst.getInt(4);
        listOfSections.add(tempSection);
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
    return listOfSections;
  }
}
