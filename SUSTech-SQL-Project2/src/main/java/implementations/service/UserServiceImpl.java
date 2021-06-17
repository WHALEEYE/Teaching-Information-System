package implementations.service;

import cn.edu.sustech.cs307.database.SQLDataSource;
import cn.edu.sustech.cs307.dto.Department;
import cn.edu.sustech.cs307.dto.Instructor;
import cn.edu.sustech.cs307.dto.Major;
import cn.edu.sustech.cs307.dto.Student;
import cn.edu.sustech.cs307.dto.User;
import cn.edu.sustech.cs307.exception.EntityNotFoundException;
import cn.edu.sustech.cs307.service.UserService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class UserServiceImpl implements UserService {

  private final SQLDataSource sds;

  public UserServiceImpl() {
    sds = SQLDataSource.getInstance();
  }

  @Override
  public void removeUser(int userId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("call remove_user(?)");
      pStmt.setInt(1, userId);
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
  public List<User> getAllUsers() {
    List<User> listOfUsers = new ArrayList<>();
    User tempUser;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from get_all_users() as (is_student bool, id int, name varchar, en_date date, maj_id int, maj_name varchar, dep_id int, dep_name varchar)");
      rst = pStmt.executeQuery();
      while (rst.next()) {
        if (rst.getBoolean(1)) {
          tempUser = new Student();
          ((Student) tempUser).major = new Major();
          ((Student) tempUser).major.department = new Department();
          ((Student) tempUser).enrolledDate = rst.getDate(3);
          ((Student) tempUser).major.id = rst.getInt(4);
          ((Student) tempUser).major.name = rst.getString(5);
          ((Student) tempUser).major.department.id = rst.getInt(6);
          ((Student) tempUser).major.department.name = rst.getString(7);
        } else {
          tempUser = new Instructor();
        }
        tempUser.id = rst.getInt(1);
        tempUser.fullName = rst.getString(2);
        listOfUsers.add(tempUser);
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
    return listOfUsers;
  }

  @Override
  public User getUser(int userId) {
    User tempUser = null;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from get_user(?) as (is_student bool, id int, name varchar, en_date date, maj_id int, maj_name varchar, dep_id int, dep_name varchar)");
      pStmt.setInt(1, userId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        if (rst.getBoolean(1)) {
          tempUser = new Student();
          ((Student) tempUser).major = new Major();
          ((Student) tempUser).major.department = new Department();
          ((Student) tempUser).enrolledDate = rst.getDate(3);
          ((Student) tempUser).major.id = rst.getInt(4);
          ((Student) tempUser).major.name = rst.getString(5);
          ((Student) tempUser).major.department.id = rst.getInt(6);
          ((Student) tempUser).major.department.name = rst.getString(7);
        } else {
          tempUser = new Instructor();
        }
        tempUser.id = rst.getInt(1);
        tempUser.fullName = rst.getString(2);
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
    return tempUser;
  }
}
