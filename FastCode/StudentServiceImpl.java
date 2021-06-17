package implementations.service;

import cn.edu.sustech.cs307.database.SQLDataSource;
import cn.edu.sustech.cs307.dto.Course;
import cn.edu.sustech.cs307.dto.Course.CourseGrading;
import cn.edu.sustech.cs307.dto.CourseSearchEntry;
import cn.edu.sustech.cs307.dto.CourseSection;
import cn.edu.sustech.cs307.dto.CourseSectionClass;
import cn.edu.sustech.cs307.dto.CourseTable;
import cn.edu.sustech.cs307.dto.CourseTable.CourseTableEntry;
import cn.edu.sustech.cs307.dto.Department;
import cn.edu.sustech.cs307.dto.Instructor;
import cn.edu.sustech.cs307.dto.Major;
import cn.edu.sustech.cs307.dto.grade.Grade;
import cn.edu.sustech.cs307.dto.grade.HundredMarkGrade;
import cn.edu.sustech.cs307.dto.grade.PassOrFailGrade;
import cn.edu.sustech.cs307.exception.EntityNotFoundException;
import cn.edu.sustech.cs307.exception.IntegrityViolationException;
import cn.edu.sustech.cs307.service.InstructorService;
import cn.edu.sustech.cs307.service.StudentService;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class StudentServiceImpl implements StudentService {

  private final SQLDataSource sds;
  private final Grade.Cases<Short> gCase;

  public StudentServiceImpl() {
    sds = SQLDataSource.getInstance();
    gCase = new Grade.Cases<>() {
      @Override
      public Short match(PassOrFailGrade self) {
        if (self == PassOrFailGrade.PASS) {
          return -1;
        } else {
          return -2;
        }
      }

      @Override
      public Short match(HundredMarkGrade self) {
        return self.mark;
      }
    };
  }

  @Override
  public void addStudent(int userId, int majorId, String firstName, String lastName,
      Date enrolledDate) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "insert into student (id, major_id, first_name, last_name, full_name, enrolled_date) values (?, ?, ?, ?, default, ?)");
      pStmt.setInt(1, userId);
      pStmt.setInt(2, majorId);
      pStmt.setString(3, firstName);
      pStmt.setString(4, lastName);
      pStmt.setDate(5, enrolledDate);
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
  public List<CourseSearchEntry> searchCourse(int studentId, int semesterId,
      @Nullable String searchCid, @Nullable String searchName, @Nullable String searchInstructor,
      @Nullable DayOfWeek searchDayOfWeek, @Nullable Short searchClassTime,
      @Nullable List<String> searchClassLocations, CourseType searchCourseType, boolean ignoreFull,
      boolean ignoreConflict, boolean ignorePassed, boolean ignoreMissingPrerequisites,
      int pageSize, int pageIndex) {
    List<CourseSearchEntry> courseSearchResult = new ArrayList<>();
    CourseSearchEntry tempEntry;
    CourseSectionClass tempClass;
    short weekCnt;
    int weekListNum, sType;
    Connection conn = null;
    PreparedStatement pStmt = null/*, conflictPStmt = null*/;
    ResultSet /*conflictRst = null,*/ rst = null;
    boolean stop;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from search_course(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) as (course_id varchar, course_name varchar, credit int, class_hour int, is_pf_grading bool, section_id int, section_name varchar, total_capacity int, left_capacity int,  class_id int, ins_id int, ins_name varchar, day_of_week int, week_list int, class_begin int, class_end int, location varchar, e_full_name varchar, type_int int, a_full_name varchar)");
      pStmt.setInt(1, studentId);
      pStmt.setInt(2, semesterId);
      if (searchCid == null) {
        pStmt.setNull(3, Types.VARCHAR);
      } else {
        pStmt.setString(3, searchCid);
      }
      if (searchName == null) {
        pStmt.setNull(4, Types.VARCHAR);
      } else {
        pStmt.setString(4, searchName);
      }
      if (searchInstructor == null) {
        pStmt.setNull(5, Types.VARCHAR);
      } else {
        pStmt.setString(5, searchInstructor);
      }
      if (searchDayOfWeek == null) {
        pStmt.setNull(6, Types.INTEGER);
      } else {
        pStmt.setInt(6, searchDayOfWeek.getValue());
      }
      if (searchClassTime == null) {
        pStmt.setNull(7, Types.INTEGER);
      } else {
        pStmt.setInt(7, searchClassTime);
      }
      if (searchClassLocations == null) {
        pStmt.setNull(8, Types.ARRAY);
      } else {
        pStmt.setArray(8,
            conn.createArrayOf("varchar", searchClassLocations.toArray(new String[0])));
      }
      switch (searchCourseType) {
        case ALL:
          sType = 1;
          break;
        case MAJOR_COMPULSORY:
          sType = 2;
          break;
        case MAJOR_ELECTIVE:
          sType = 3;
          break;
        case CROSS_MAJOR:
          sType = 4;
          break;
        case PUBLIC:
          sType = 5;
          break;
        default:
          sType = 0;
          break;
      }
      pStmt.setInt(9, sType);
      pStmt.setBoolean(10, ignoreFull);
      pStmt.setBoolean(11, ignoreConflict);
      pStmt.setBoolean(12, ignorePassed);
      pStmt.setBoolean(13, ignoreMissingPrerequisites);
      pStmt.setInt(14, pageSize);
      pStmt.setInt(15, pageIndex);
      rst = pStmt.executeQuery();
      /*
      conflictPStmt = conn.prepareStatement(
          "select * from get_enrolled_conflict_sections(?, ?) as (sec_full_name varchar)");*/
      if (rst.next()) {
        stop = false;
        while (!stop) {
          tempEntry = new CourseSearchEntry();
          tempEntry.course = new Course();
          tempEntry.sectionClasses = new HashSet<>();
          tempEntry.section = new CourseSection();
          tempEntry.conflictCourseNames = new ArrayList<>();
          tempEntry.course.id = rst.getString(1);
          tempEntry.course.name = rst.getString(2);
          tempEntry.course.credit = rst.getInt(3);
          tempEntry.course.classHour = rst.getInt(4);
          tempEntry.course.grading =
              rst.getBoolean(5) ? CourseGrading.PASS_OR_FAIL : CourseGrading.HUNDRED_MARK_SCORE;
          tempEntry.section.id = rst.getInt(6);
          tempEntry.section.name = rst.getString(7);
          tempEntry.section.totalCapacity = rst.getInt(8);
          tempEntry.section.leftCapacity = rst.getInt(9);

          /*
          // get conflict sections
          conflictPStmt.setInt(1, studentId);
          conflictPStmt.setInt(2, tempEntry.section.id);
          conflictRst = conflictPStmt.executeQuery();
          while (conflictRst.next()) {
            tempEntry.conflictCourseNames.add(conflictRst.getString(1));
          }
          conflictRst.close();
          */

          /*
          // get classes
          while (rst.getInt(6) == tempEntry.section.id) {
            tempClass = new CourseSectionClass();
            tempClass.instructor = new Instructor();
            tempClass.weekList = new HashSet<>();
            tempClass.id = rst.getInt(10);
            tempClass.instructor.id = rst.getInt(11);
            tempClass.instructor.fullName = rst.getString(12);
            tempClass.dayOfWeek = DayOfWeek.of(rst.getInt(13));
            weekListNum = rst.getInt(14);
            weekCnt = 0;
            while (weekListNum > 0) {
              weekCnt++;
              if (weekListNum % 2 == 1) {
                tempClass.weekList.add(weekCnt);
              }
              weekListNum >>= 1;
            }
            tempClass.classBegin = rst.getShort(15);
            tempClass.classEnd = rst.getShort(16);
            tempClass.location = rst.getString(17);
            tempEntry.sectionClasses.add(tempClass);
            if (!rst.next()) {
              stop = true;
              break;
            }
          }*/

          while (rst.getInt(6) == tempEntry.section.id) {
            if(rst.getInt(19) == 0) {
              // get classes
              tempClass = new CourseSectionClass();
              tempClass.instructor = new Instructor();
              tempClass.weekList = new HashSet<>();
              tempClass.id = rst.getInt(10);
              tempClass.instructor.id = rst.getInt(11);
              tempClass.instructor.fullName = rst.getString(12);
              tempClass.dayOfWeek = DayOfWeek.of(rst.getInt(13));
              weekListNum = rst.getInt(14);
              weekCnt = 0;
              while (weekListNum > 0) {
                weekCnt++;
                if (weekListNum % 2 == 1) {
                  tempClass.weekList.add(weekCnt);
                }
                weekListNum >>= 1;
              }
              tempClass.classBegin = rst.getShort(15);
              tempClass.classEnd = rst.getShort(16);
              tempClass.location = rst.getString(17);
              tempEntry.sectionClasses.add(tempClass);
            } else if (rst.getInt(19) == 1) {
              // get conflict sections
              tempEntry.conflictCourseNames.add(rst.getString(18));
            }

            if (!rst.next()) {
              stop = true;
              break;
            }
          }
          courseSearchResult.add(tempEntry);
        }
      }
      /*conflictPStmt.close();*/
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        /*
        if (conflictRst != null) {
          conflictRst.close();
        }
        if (conflictPStmt != null) {
          conflictPStmt.close();
        }*/
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
    return courseSearchResult;
  }

  @Override
  public EnrollResult enrollCourse(int studentId, int sectionId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select enroll_course(?, ?)");
      pStmt.setInt(1, studentId);
      pStmt.setInt(2, sectionId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        switch (rst.getInt(1)) {
          case 0:
            return EnrollResult.UNKNOWN_ERROR;
          case 1:
            return EnrollResult.COURSE_NOT_FOUND;
          case 2:
            return EnrollResult.ALREADY_ENROLLED;
          case 3:
            return EnrollResult.ALREADY_PASSED;
          case 4:
            return EnrollResult.PREREQUISITES_NOT_FULFILLED;
          case 5:
            return EnrollResult.COURSE_CONFLICT_FOUND;
          case 6:
            return EnrollResult.COURSE_IS_FULL;
          default:
            return EnrollResult.SUCCESS;
        }
      }
    } catch (SQLException e) {
      return EnrollResult.UNKNOWN_ERROR;
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
    return EnrollResult.UNKNOWN_ERROR;
  }

  @Override
  public void dropCourse(int studentId, int sectionId) throws IllegalStateException {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    boolean dropped;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select drop_course(?, ?)");
      pStmt.setInt(1, studentId);
      pStmt.setInt(2, sectionId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        dropped = rst.getBoolean(1);
        if (!dropped) {
          throw new IllegalStateException();
        }
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

  }

  @Override
  public void addEnrolledCourseWithGrade(int studentId, int sectionId, @Nullable Grade grade) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    boolean added;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select add_enrolled_course(?, ?, ?)");
      pStmt.setInt(1, studentId);
      pStmt.setInt(2, sectionId);
      if (grade == null) {
        pStmt.setNull(3, Types.INTEGER);
      } else {
        pStmt.setInt(3, grade.when(gCase));
      }
      rst = pStmt.executeQuery();
      if (rst.next()) {
        added = rst.getBoolean(1);
        if (!added) {
          throw new IntegrityViolationException();
        }
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
  }

  @Override
  public void setEnrolledCourseGrade(int studentId, int sectionId, Grade grade) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    boolean set;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select set_grade(?, ?, ?)");
      pStmt.setInt(1, studentId);
      pStmt.setInt(2, sectionId);
      pStmt.setInt(3, grade.when(gCase));
      rst = pStmt.executeQuery();
      if (rst.next()) {
        set = rst.getBoolean(1);
        if (!set) {
          throw new IntegrityViolationException();
        }
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
  }

  @Override
  public Map<Course, Grade> getEnrolledCoursesAndGrades(int studentId,
      @Nullable Integer semesterId) {
    Map<Course, Grade> tempMap = new HashMap<>();
    Course tempCourse;
    Grade tempGrade;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from get_enrolled_courses(?, ?) as (id varchar, name varchar, credit int, class_hour int, is_pf_grading bool, grade int)");
      pStmt.setInt(1, studentId);
      if (semesterId == null) {
        pStmt.setNull(2, Types.INTEGER);
      } else {
        pStmt.setInt(2, semesterId);
      }
      rst = pStmt.executeQuery();
      while (rst.next()) {
        tempCourse = new Course();
        tempCourse.id = rst.getString(1);
        if (rst.wasNull()) {
          throw new EntityNotFoundException();
        }
        tempCourse.name = rst.getString(2);
        tempCourse.credit = rst.getInt(3);
        tempCourse.classHour = rst.getInt(4);
        tempCourse.grading =
            rst.getBoolean(5) ? CourseGrading.PASS_OR_FAIL : CourseGrading.HUNDRED_MARK_SCORE;
        switch (rst.getShort(6)) {
          case -1:
            tempGrade = PassOrFailGrade.PASS;
            break;
          case -2:
            tempGrade = PassOrFailGrade.FAIL;
            break;
          default:
            tempGrade = new HundredMarkGrade(rst.getShort(6));
            break;
        }
        tempMap.put(tempCourse, tempGrade);
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
    return tempMap;
  }

  @Override
  public CourseTable getCourseTable(int studentId, Date date) {
    CourseTable tempTable = new CourseTable();
    tempTable.table = new HashMap<>();
    tempTable.table.put(DayOfWeek.MONDAY, new HashSet<>());
    tempTable.table.put(DayOfWeek.TUESDAY, new HashSet<>());
    tempTable.table.put(DayOfWeek.WEDNESDAY, new HashSet<>());
    tempTable.table.put(DayOfWeek.THURSDAY, new HashSet<>());
    tempTable.table.put(DayOfWeek.FRIDAY, new HashSet<>());
    tempTable.table.put(DayOfWeek.SATURDAY, new HashSet<>());
    tempTable.table.put(DayOfWeek.SUNDAY, new HashSet<>());

    Instructor tempIns;
    CourseTable.CourseTableEntry tempEntry;
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select * from get_section_table(?, ?) as (section_name varchar, ins_id int, ins_name varchar, begin_time int, end_time int, location varchar, dow int)");
      pStmt.setInt(1, studentId);
      pStmt.setDate(2, date);
      rst = pStmt.executeQuery();
      while (rst.next()) {
        tempEntry = new CourseTableEntry();
        tempEntry.courseFullName = rst.getString(1);
        tempIns = new Instructor();
        tempIns.id = rst.getInt(2);
        tempIns.fullName = rst.getString(3);
        tempEntry.instructor = tempIns;
        tempEntry.classBegin = rst.getShort(4);
        tempEntry.classEnd = rst.getShort(5);
        tempEntry.location = rst.getString(6);
        tempTable.table.get(DayOfWeek.of(rst.getInt(7))).add(tempEntry);
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
    return tempTable;
  }

  @Override
  public boolean passedPrerequisitesForCourse(int studentId, String courseId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    boolean satisfy = false;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement("select judge_prerequisite(?, ?)");
      pStmt.setInt(1, studentId);
      pStmt.setString(2, courseId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        satisfy = rst.getBoolean(1);
        if (rst.wasNull()) {
          throw new EntityNotFoundException();
        }
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
    return satisfy;
  }

  @Override
  public Major getStudentMajor(int studentId) {
    Connection conn = null;
    PreparedStatement pStmt = null;
    ResultSet rst = null;
    Major tempMajor = null;
    try {
      conn = sds.getSQLConnection();
      pStmt = conn.prepareStatement(
          "select m.id, m.name, d.id, d.name from (select major_id from student where id = ?) x join major m on x.major_id = m.id join department d on d.id = m.department_id");
      pStmt.setInt(1, studentId);
      rst = pStmt.executeQuery();
      if (rst.next()) {
        tempMajor = new Major();
        tempMajor.department = new Department();
        tempMajor.id = rst.getInt(1);
        tempMajor.name = rst.getString(2);
        tempMajor.department.id = rst.getInt(3);
        tempMajor.department.name = rst.getString(4);
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
}
