package implementations.database;

import cn.edu.sustech.cs307.database.SQLDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class PreparedStatementCache {

  private static final PreparedStatementCache INSTANCE = new PreparedStatementCache();
  private static final int MAX_SIZE = 14;
  private final SQLDataSource sds;
  private final Entry[] table;
  private final Entry[] cache;
  private int entryCnt;

  private PreparedStatementCache() {
    sds = SQLDataSource.getInstance();
    entryCnt = 0;
    cache = new Entry[MAX_SIZE];
    table = new Entry[50];
    //CourseService -- addCourse()
    table[0] = new Entry("call add_course(?, ?, ?, ?, ?, ?)");
//    table[1] = new Entry("");
    //CourseService -- addCourseSection()
    table[2] = new Entry(
        "insert into course_section (course_id, semester_id, name, total_capacity, left_capacity) values (?, ?, ?, ?, ?) returning id");
    //CourseService -- addCourseSectionClass()
    table[3] = new Entry(
        "insert into course_section_class (section_id, instructor_id, day_of_week, week_list, class_begin, class_end, location) values (?, ?, ?, ?, ?, ?, ?) returning id");
    //CourseService -- removeCourse()
    table[4] = new Entry("delete from course where id = ?");
    //CourseService -- removeCourseSection()
    table[5] = new Entry("delete from course_section where id = ?");
    //CourseService -- removeCourseSectionClass()
    table[6] = new Entry("delete from course_section_class where id = ?");
    //CourseService -- getAllCourses()
    table[7] = new Entry("select id, name, credit, class_hour, is_pf_grading from course");
    //CourseService -- getCourseSectionsInSemester() $$ ps:index created
    table[8] = new Entry(
        "select * from get_course_sections_in_semester(?, ?) as (id int, name varchar, total_capacity int, left_capacity int)");
    //CourseService -- getCourseBySection()
    table[9] = new Entry(
        "select c.id, c.name, c.credit, c.class_hour, c.is_pf_grading from course_section cs join course c on c.id = cs.course_id where cs.id = ?");
    //CourseService -- getCourseSectionClasses() $$ ps:index created
    table[10] = new Entry(
        "select * from get_course_section_classes(?) as (id int, ins_id int, ins_name varchar, day_of_week int, week_list int, class_begin int, class_end int, location varchar)");
    //CourseService -- getCourseSectionByClass()
    table[11] = new Entry(
        "select cs.id, cs.name, cs.total_capacity, cs.left_capacity from course_section_class csc join course_section cs on cs.id = csc.section_id where csc.id = ?");
    //CourseService -- getEnrolledStudentsInSemester()
    table[12] = new Entry(
        "select * from get_enrolled_students_in_semester(?, ?) as (stu_id int, stu_name varchar, enrolled_date date, maj_id int, maj_name varchar, dep_id int, dep_name varchar)");
    //DepartmentService -- addDepartment()
    table[13] = new Entry("insert into department (name) values (?) returning id");
    //DepartmentService -- removeDepartment()
    table[14] = new Entry("delete from department where id = ?");
    //DepartmentService -- getAllDepartments()
    table[15] = new Entry("select id, name from department");
    //DepartmentService -- getDepartment()
    table[16] = new Entry("select name from department where id = ?");
    //InstructorService -- addInstructor()
    table[17] = new Entry("insert into instructor (id, first_name, last_name) values (?, ?, ?)");
    //InstructorService -- getInstructedCourseSections() $$ ps:index created
    table[18] = new Entry(
        "select * from get_instructed_course_sections(?, ?) as (sec_id int, sec_name varchar, total_capacity int, left_capacity int)");
    //MajorService -- addMajor()
    table[19] = new Entry("insert into major (name, department_id) values (?, ?) returning id");
    //MajorService -- removeMajor()
    table[20] = new Entry("delete from major where id = ?");
    //MajorService -- getAllMajors()
    table[21] = new Entry(
        "select m.id, m.name, m.department_id, d.name from department d join major m on d.id = m.department_id");
    //MajorService -- getMajor()
    table[22] = new Entry(
        "select x.name, d.id, d.name from (select department_id, name from major where id = ?) x join department d on x.department_id = d.id");
    //MajorService -- addMajorCompulsoryCourse()
    table[23] = new Entry(
        "insert into major_course_relations (major_id, course_id, is_compulsory) values (?, ?, true)");
    //MajorService -- addMajorElectiveCourse()
    table[24] = new Entry(
        "insert into major_course_relations (major_id, course_id, is_compulsory) values (?, ?, false)");
    //SemesterService -- addSemester()
    table[25] = new Entry(
        "insert into semester (name, begin_date, end_date) values (?, ?, ?) returning id");
    //SemesterService -- removeSemester()
    table[26] = new Entry("delete from semester where id = ?");
    //SemesterService -- getAllSemesters()
    table[27] = new Entry("select id, name, begin_date, end_date from semester");
    //SemesterService -- getSemester()
    table[28] = new Entry("select id, name, begin_date, end_date from semester where id = ?");
    //UserService -- removeUser()
    table[29] = new Entry("call remove_user(?)");
    //UserService -- getAllUsers()
    table[30] = new Entry(
        "select * from get_all_users() as (is_student bool, id int, name varchar, en_date date, maj_id int, maj_name varchar, dep_id int, dep_name varchar)");
    //UserService -- getUser()
    table[31] = new Entry(
        "select * from get_user(?) as (is_student bool, id int, name varchar, en_date date, maj_id int, maj_name varchar, dep_id int, dep_name varchar)");
    //StudentServiceImpl -- addStudent()
    table[32] = new Entry("insert into student (id, major_id, first_name, last_name, enrolled_date) values (?, ?, ?, ?, ?)");
    //StudentServiceImpl -- searchCourse()
    table[33] = new Entry("select * from search_course(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) as (course_id varchar, course_name varchar, credit int, class_hour int, is_pf_grading bool, section_id int, section_name varchar, total_capacity int, left_capacity int,  class_id int, ins_id int, ins_name varchar, day_of_week int, week_list int, class_begin int, class_end int, location varchar)");
    //StudentServiceImpl --
    //table[34] = new Entry();
    //StudentServiceImpl -- enrollCourse()
    table[35] = new Entry("select enroll_course(?, ?)");
    //StudentServiceImpl -- dropCourse()
    table[36] = new Entry("select drop_course(?, ?)");
    //StudentServiceImpl -- addEnrolledCourseWithGrade()
    table[37] = new Entry("select add_enrolled_course(?, ?, ?)");
    //StudentServiceImpl -- setEnrolledCourseGrade()
    table[38] = new Entry("select set_grade(?, ?, ?)");
    //StudentServiceImpl -- getEnrolledCoursesAndGrades()
    table[39] = new Entry("select * from get_enrolled_courses(?, ?) as (id varchar, name varchar, credit int, class_hour int, is_pf_grading bool, grade int)");
    //StudentServiceImpl -- getCourseTable()
    //table[40] = new Entry();
    //StudentServiceImpl -- passedPrerequisitesForCourse()
    table[41] = new Entry("select judge_prerequisite(?, ?)");
    //StudentServiceImpl -- getStudentMajor()
    table[42] = new Entry("select m.id, m.name, d.id, d.name from (select major_id from student where id = ?) x join major m on x.major_id = m.id join department d on d.id = m.department_id");

  }

  public static PreparedStatementCache getInstance() {
    return INSTANCE;
  }

  public PreparedStatement getPreparedStatement(int index) throws SQLException {
    if (table[index].pStmt == null) {
      if (entryCnt == MAX_SIZE) {
        cache[index % MAX_SIZE].closePreparedStatement();
        cache[index % MAX_SIZE] = table[index];
      } else {
        cache[entryCnt++] = table[index];
      }
      return table[index].openPreparedStatement(sds.getSQLConnection());
    } else {
      return table[index].pStmt;
    }
  }

  private class Entry {

    private final String sql;
    private PreparedStatement pStmt;
    private Connection conn;

    private Entry(String sql) {
      this.sql = sql;
    }

    private PreparedStatement openPreparedStatement(Connection conn) throws SQLException {
      this.conn = conn;
      this.pStmt = conn.prepareStatement(this.sql);
      return this.pStmt;
    }

    private void closePreparedStatement() throws SQLException {
      this.conn.close();
      this.pStmt.close();
      this.conn = null;
      this.pStmt = null;
    }

  }
}
