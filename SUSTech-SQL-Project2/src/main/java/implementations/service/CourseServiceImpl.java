package implementations.service;

import cn.edu.sustech.cs307.database.SQLDataSource;
import cn.edu.sustech.cs307.dto.Course;
import cn.edu.sustech.cs307.dto.Course.CourseGrading;
import cn.edu.sustech.cs307.dto.CourseSection;
import cn.edu.sustech.cs307.dto.CourseSectionClass;
import cn.edu.sustech.cs307.dto.Department;
import cn.edu.sustech.cs307.dto.Instructor;
import cn.edu.sustech.cs307.dto.Major;
import cn.edu.sustech.cs307.dto.Student;
import cn.edu.sustech.cs307.dto.prerequisite.AndPrerequisite;
import cn.edu.sustech.cs307.dto.prerequisite.CoursePrerequisite;
import cn.edu.sustech.cs307.dto.prerequisite.OrPrerequisite;
import cn.edu.sustech.cs307.dto.prerequisite.Prerequisite;
import cn.edu.sustech.cs307.dto.prerequisite.Prerequisite.Cases;
import cn.edu.sustech.cs307.exception.EntityNotFoundException;
import cn.edu.sustech.cs307.exception.IntegrityViolationException;
import cn.edu.sustech.cs307.service.CourseService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class CourseServiceImpl implements CourseService {

  private final Prerequisite.Cases<String> pCase;
  private final SQLDataSource sds;

  public CourseServiceImpl() {
    sds = SQLDataSource.getInstance();
    pCase = new Cases<>() {
      StringBuilder temp;

      @Override
      public String match(AndPrerequisite self) {
        String[] children = self.terms.stream()
            .map(term -> term.when(this))
            .toArray(String[]::new);
        temp = new StringBuilder(children[0]);
        for (int i = 1; i < children.length; i++) {
          temp.append("|").append(children[i]).append("|AND");
        }
        return temp.toString();
      }

      @Override
      public String match(OrPrerequisite self) {
        String[] children = self.terms.stream()
            .map(term -> term.when(this))
            .toArray(String[]::new);
        temp = new StringBuilder(children[0]);
        for (int i = 1; i < children.length; i++) {
          temp.append("|").append(children[i]).append("|OR");
        }
        return temp.toString();
      }

      @Override
      public String match(CoursePrerequisite self) {
        return self.courseID;
      }
    };
  }

  @Override
  public void addCourse(String courseId, String courseName, int credit, int classHour,
      CourseGrading grading, @Nullable Prerequisite prerequisite) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("call add_course(?, ?, ?, ?, ?, ?)");
      pStmt.setString(1, courseId);
      pStmt.setString(2, courseName);
      pStmt.setInt(3, credit);
      pStmt.setInt(4, classHour);
      pStmt.setBoolean(5, grading == CourseGrading.PASS_OR_FAIL);
      if (prerequisite != null) {
        pStmt.setString(6, prerequisite.when(pCase));
      } else {
        pStmt.setNull(6, Types.VARCHAR);
      }
      pStmt.execute();
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
  public int addCourseSection(String courseId, int semesterId, String sectionName,
      int totalCapacity) {
    int id = -1;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "insert into course_section (course_id, semester_id, name, total_capacity, left_capacity) values (?, ?, ?, ?, ?) returning id");
      pStmt.setString(1, courseId);
      pStmt.setInt(2, semesterId);
      pStmt.setString(3, sectionName);
      pStmt.setInt(4, totalCapacity);
      pStmt.setInt(5, totalCapacity);
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
  public int addCourseSectionClass(int sectionId, int instructorId, DayOfWeek dayOfWeek,
      Set<Short> weekList, short classStart, short classEnd, String location) {
    int id = -1;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    int weekListNum = 0;
    for (Short aShort : weekList) {
      weekListNum += (1 << (aShort - 1));
    }
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "insert into course_section_class (section_id, instructor_id, day_of_week, week_list, class_begin, class_end, location) values (?, ?, ?, ?, ?, ?, ?) returning id");
      pStmt.setInt(1, sectionId);
      pStmt.setInt(2, instructorId);
      pStmt.setInt(3, dayOfWeek.getValue());
      pStmt.setInt(4, weekListNum);
      pStmt.setInt(5, classStart);
      pStmt.setInt(6, classEnd);
      pStmt.setString(7, location);
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
  public void removeCourse(String courseId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("delete from course where id = ?");
      pStmt.setString(1, courseId);
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
  public void removeCourseSection(int sectionId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("delete from course_section where id = ?");
      pStmt.setInt(1, sectionId);
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
  public void removeCourseSectionClass(int classId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("delete from course_section_class where id = ?");
      pStmt.setInt(1, classId);
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
  public List<Course> getAllCourses() {
    List<Course> listOfCourses = new ArrayList<>();
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    Course tempCourse;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn
          .prepareStatement("select id, name, credit, class_hour, is_pf_grading from course");
      rst = pStmt.executeQuery();
      while (rst.next()) {
        tempCourse = new Course();
        tempCourse.id = rst.getString(1);
        tempCourse.name = rst.getString(2);
        tempCourse.credit = rst.getInt(3);
        tempCourse.classHour = rst.getInt(4);
        tempCourse.grading =
            rst.getBoolean(5) ? CourseGrading.PASS_OR_FAIL : CourseGrading.HUNDRED_MARK_SCORE;
        listOfCourses.add(tempCourse);
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
    return listOfCourses;
  }

  @Override
  public List<CourseSection> getCourseSectionsInSemester(String courseId, int semesterId) {
    CourseSection tempSection;
    List<CourseSection> listOfSections = new ArrayList<>();
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from get_course_sections_in_semester(?, ?) as (id int, name varchar, total_capacity int, left_capacity int)");
      pStmt.setString(1, courseId);
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

  @Override
  public Course getCourseBySection(int sectionId) {
    Course tempCourse = null;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select c.id, c.name, c.credit, c.class_hour, c.is_pf_grading from course_section cs join course c on c.id = cs.course_id where cs.id = ?");
      pStmt.setInt(1, sectionId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        tempCourse = new Course();
        tempCourse.id = rst.getString(1);
        tempCourse.name = rst.getString(2);
        tempCourse.credit = rst.getInt(3);
        tempCourse.classHour = rst.getInt(4);
        tempCourse.grading =
            rst.getBoolean(5) ? CourseGrading.PASS_OR_FAIL : CourseGrading.HUNDRED_MARK_SCORE;
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
    return tempCourse;
  }

  @Override
  public List<CourseSectionClass> getCourseSectionClasses(int sectionId) {
    List<CourseSectionClass> listOfClasses = new ArrayList<>();
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    int weekListNum;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from get_course_section_classes(?) as (id int, ins_id int, ins_name varchar, day_of_week int, week_list int, class_begin int, class_end int, location varchar)");
      pStmt.setInt(1, sectionId);
      rst = pStmt.executeQuery();
      while (rst.next()) {
        // used in getCourseSectionClasses()
        CourseSectionClass tempClass = new CourseSectionClass();
        tempClass.instructor = new Instructor();
        tempClass.weekList = new HashSet<>();
        tempClass.id = rst.getInt(1);
        if (rst.wasNull()) {
          throw new EntityNotFoundException();
        }
        tempClass.instructor.id = rst.getInt(2);
        tempClass.instructor.fullName = rst.getString(3);
        tempClass.dayOfWeek = DayOfWeek.of(rst.getInt(4));
        weekListNum = rst.getInt(5);
        short weekCnt = 0;
        while (weekListNum > 0) {
          weekCnt++;
          if (weekListNum % 2 == 1) {
            tempClass.weekList.add(weekCnt);
          }
          weekListNum >>= 1;
        }
        tempClass.classBegin = rst.getShort(6);
        tempClass.classEnd = rst.getShort(7);
        tempClass.location = rst.getString(8);
        listOfClasses.add(tempClass);
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
    return listOfClasses;
  }

  @Override
  public CourseSection getCourseSectionByClass(int classId) {
    CourseSection tempSection = null;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select cs.id, cs.name, cs.total_capacity, cs.left_capacity from course_section_class csc join course_section cs on cs.id = csc.section_id where csc.id = ?");
      pStmt.setInt(1, classId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        tempSection = new CourseSection();
        tempSection.id = rst.getInt(1);
        tempSection.name = rst.getString(2);
        tempSection.totalCapacity = rst.getInt(3);
        tempSection.leftCapacity = rst.getInt(4);
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
    return tempSection;
  }

  @Override
  public List<Student> getEnrolledStudentsInSemester(String courseId, int semesterId) {
    List<Student> listOfStudents = new ArrayList<>();
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from get_enrolled_students_in_semester(?, ?) as (stu_id int, stu_name varchar, enrolled_date date, maj_id int, maj_name varchar, dep_id int, dep_name varchar)");
      pStmt.setString(1, courseId);
      pStmt.setInt(2, semesterId);
      rst = pStmt.executeQuery();
      while (rst.next()) {
        //used in getEnrolledStudentsInSemester()
        Student tempStudent = new Student();
        tempStudent.major = new Major();
        tempStudent.major.department = new Department();
        tempStudent.id = rst.getInt(1);
        if (rst.wasNull()) {
          throw new EntityNotFoundException();
        }
        tempStudent.fullName = rst.getString(2);
        tempStudent.enrolledDate = rst.getDate(3);
        tempStudent.major.id = rst.getInt(4);
        tempStudent.major.name = rst.getString(5);
        tempStudent.major.department.id = rst.getInt(6);
        tempStudent.major.department.name = rst.getString(7);
        listOfStudents.add(tempStudent);
      }
    } catch (
        SQLException e) {
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
    return listOfStudents;
  }
}
