package implementations.factory;

import cn.edu.sustech.cs307.factory.ServiceFactory;
import cn.edu.sustech.cs307.service.CourseService;
import cn.edu.sustech.cs307.service.DepartmentService;
import cn.edu.sustech.cs307.service.InstructorService;
import cn.edu.sustech.cs307.service.MajorService;
import cn.edu.sustech.cs307.service.SemesterService;
import cn.edu.sustech.cs307.service.StudentService;
import cn.edu.sustech.cs307.service.UserService;
import implementations.service.CourseServiceImpl;
import implementations.service.DepartmentServiceImpl;
import implementations.service.InstructorServiceImpl;
import implementations.service.MajorServiceImpl;
import implementations.service.SemesterServiceImpl;
import implementations.service.StudentServiceImpl;
import implementations.service.UserServiceImpl;

public class ServiceFactoryImpl extends ServiceFactory {

  public ServiceFactoryImpl() {
    registerService(CourseService.class, new CourseServiceImpl());
    registerService(DepartmentService.class, new DepartmentServiceImpl());
    registerService(InstructorService.class, new InstructorServiceImpl());
    registerService(MajorService.class, new MajorServiceImpl());
    registerService(SemesterService.class, new SemesterServiceImpl());
    registerService(StudentService.class, new StudentServiceImpl());
    registerService(UserService.class, new UserServiceImpl());
  }
}
