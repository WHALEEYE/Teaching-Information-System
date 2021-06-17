drop table if exists course,
    course_prerequisite_relations,
    course_section,course_section_class,
    department,
    instructor,
    major,
    major_course_relations,
    semester,
    student,
    student_section_relations
    cascade;

create table if not exists course
(
    id            varchar not null
        constraint course_pkey
            primary key,
    name          varchar not null,
    credit        integer not null,
    class_hour    integer not null,
    is_pf_grading boolean not null
);

create table if not exists course_prerequisite_relations
(
    id              serial  not null
        constraint course_prerequisite_relations_pkey
            primary key,
    course_id       varchar not null
        constraint course_prerequisite_relations_course_id_fkey
            references course
            on delete cascade,
    prerequisite_id varchar
        constraint course_prerequisite_relations_prerequisite_id_fkey
            references course
            on delete cascade,
    and_logic       boolean,
    constraint prerequisite_or_logic
        check (((prerequisite_id IS NOT NULL) AND (and_logic IS NULL)) OR
               ((prerequisite_id IS NULL) AND (and_logic IS NOT NULL)))
);

create table if not exists semester
(
    id         serial  not null
        constraint semester_pkey
            primary key,
    name       varchar not null
        constraint semester_name_key
            unique,
    begin_date date    not null,
    end_date   date    not null
);

create table if not exists department
(
    id   serial  not null
        constraint department_pkey
            primary key,
    name varchar not null
        constraint department_name_key
            unique
);

create table if not exists major
(
    id            serial  not null
        constraint major_pkey
            primary key,
    name          varchar not null
        constraint major_name_key
            unique,
    department_id integer not null
        constraint major_department_id_fkey
            references department
            on delete cascade
);

create table if not exists major_course_relations
(
    major_id      integer not null
        constraint major_course_relations_major_id_fkey
            references major
            on delete cascade,
    course_id     varchar not null
        constraint major_course_relations_course_id_fkey
            references course
            on delete cascade,
    is_compulsory boolean not null,
    constraint course_major_primary_key
        primary key (major_id, course_id)
);

create table if not exists instructor
(
    id         integer not null
        constraint instructor_pkey
            primary key,
    first_name varchar not null,
    last_name  varchar not null,
    full_name  varchar not null,
    other_name varchar not null
);

create trigger update_instructor_full_name
    before insert or update
    on instructor
    for each row
execute procedure generate_instructor_full_name();

create table if not exists student
(
    id            integer not null
        constraint student_pkey
            primary key,
    first_name    varchar not null,
    last_name     varchar not null,
    full_name     varchar not null,
    enrolled_date date    not null,
    major_id      integer not null
        constraint student_major_id_fkey
            references major
            on delete cascade
);

create trigger update_student_full_name
    before insert or update
    on student
    for each row
execute procedure generate_student_full_name();

create table if not exists course_section
(
    id             serial  not null
        constraint course_section_pkey
            primary key,
    course_id      varchar not null
        constraint course_section_course_id_fkey
            references course
            on delete cascade,
    semester_id    integer not null
        constraint course_section_semester_id_fkey
            references semester
            on delete cascade,
    name           varchar not null,
    full_name      varchar not null,
    total_capacity integer not null,
    left_capacity  integer not null
);

create trigger update_section_full_name
    before insert or update
    on course_section
    for each row
execute procedure generate_section_full_name();

create table if not exists course_section_class
(
    id            serial  not null
        constraint course_section_class_pkey
            primary key,
    section_id    integer not null
        constraint course_section_class_section_id_fkey
            references course_section
            on delete cascade,
    instructor_id integer not null
        constraint course_section_class_instructor_id_fkey
            references instructor
            on delete cascade,
    day_of_week   integer not null,
    week_list     integer not null,
    class_begin   integer not null,
    class_end     integer not null,
    location      varchar not null
);

create table if not exists student_section_relations
(
    student_id integer not null
        constraint student_section_relations_student_id_fkey
            references student
            on delete cascade,
    section_id integer not null
        constraint student_section_relations_section_id_fkey
            references course_section
            on delete cascade,
    grade      integer,
    constraint student_section_primary_key
        primary key (student_id, section_id)
);

create index idx_course_section_class_section_id on course_section_class(section_id);
create index idx_course_section_course_id_semester_id on course_section(course_id, semester_id);
create index idx_course_section_class_instructor_id on course_section_class (instructor_id);


