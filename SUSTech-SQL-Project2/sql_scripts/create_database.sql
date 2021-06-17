--
-- PostgreSQL database dump
--

-- Dumped from database version 13.3
-- Dumped by pg_dump version 13.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: add_course(character varying, character varying, integer, integer, boolean, character varying); Type: PROCEDURE; Schema: public; Owner: whaleeye
--

CREATE PROCEDURE public.add_course(cour_id character varying, cour_name character varying, cour_credit integer, cour_ch integer, cour_pf boolean, cour_pre character varying)
    LANGUAGE plpgsql
    AS $$
declare
    temp_array varchar[];
    i          int;
begin
    insert into course (id, name, credit, class_hour, is_pf_grading)
    values (cour_id, cour_name, cour_credit, cour_ch, cour_pf);

    if cour_pre is not null then
        select regexp_split_to_array(cour_pre, E'\\|') into temp_array;
        for i in 1 .. array_length(temp_array, 1)
            loop
                if temp_array[i] = 'AND' then
                    insert into course_prerequisite_relations(course_id, prerequisite_id, and_logic)
                    VALUES (cour_id, null, true);
                elseif temp_array[i] = 'OR' then
                    insert into course_prerequisite_relations(course_id, prerequisite_id, and_logic)
                    VALUES (cour_id, null, false);
                else
                    insert into course_prerequisite_relations(course_id, prerequisite_id, and_logic)
                    VALUES (cour_id, temp_array[i], null);
                end if;
            end loop;
    end if;
end;
$$;


ALTER PROCEDURE public.add_course(cour_id character varying, cour_name character varying, cour_credit integer, cour_ch integer, cour_pf boolean, cour_pre character varying) OWNER TO whaleeye;

--
-- Name: add_enrolled_course(integer, integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.add_enrolled_course(stu_id integer, sec_id integer, grd integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare
    temp_bool bool;
begin
    if grd is null then
        insert into student_section_relations (student_id, section_id) values (stu_id, sec_id);
        return true;
    end if;

    select c.is_pf_grading
    into temp_bool
    from course_section cs
             join course c on c.id = cs.course_id
    where cs.id = sec_id;

    if (temp_bool and grd >= 0) or (not temp_bool and grd < 0) then
        return false;
    end if;

    insert into student_section_relations
        (student_id, section_id, grade)
    values (stu_id, sec_id, grd);
    return true;
end;
$$;


ALTER FUNCTION public.add_enrolled_course(stu_id integer, sec_id integer, grd integer) OWNER TO whaleeye;

--
-- Name: detect_conflict(integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.detect_conflict(stu_id integer, sec_id integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare
    course_conflict_flag bool;
    time_conflict_flag   bool;
begin
    drop table if exists student_selected_section_in_same_semester;
    create temp table if not exists student_selected_section_in_same_semester on commit drop as
    select section_id stu_sec_id, course_id, semester_id
    from (select section_id
          from student_section_relations
          where student_id = stu_id
         ) as ssr
             join course_section cs on ssr.section_id = cs.id
    where cs.semester_id = (select semester_id
                            from course_section
                            where id = sec_id);

    -- If there are conflicts return true
    -- Course Conflict
    select case when cnt = 0 then false else true end
    from (
             select count(*) as cnt
             from (
                      select stu_sec_id as sec_id_1
                      from student_selected_section_in_same_semester
                      where course_id = (
                          select course_id
                          from course_section
                          where id = sec_id
                      )
                        and semester_id = (
                          select semester_id
                          from course_section
                          where id = sec_id
                      )
                        and stu_sec_id != sec_id
                  ) as same_course_sec_id
                      join student_selected_section_in_same_semester as ssiss
                           on same_course_sec_id.sec_id_1 = ssiss.stu_sec_id) as count
    into course_conflict_flag;

    if course_conflict_flag = true then
        return true;
    end if;

    -- Time Conflict
    drop table if exists student_selected_section_time;
    create temp table if not exists student_selected_section_time on commit drop as
    select week_list, day_of_week, class_begin, class_end
    from course_section_class
    where section_id in (select stu_sec_id
                         from student_selected_section_in_same_semester);

    drop table if exists target_section_time;
    create temp table if not exists target_section_time on commit drop as
    select week_list, day_of_week, class_begin, class_end
    from course_section_class
    where section_id = sec_id;

    select case when cnt = 0 then false else true end
    from (
             select count(*) cnt
             from target_section_time tst
                      join student_selected_section_time ssst
                           on tst.week_list & ssst.week_list > 0
                               and tst.day_of_week = ssst.day_of_week
             where (not (tst.class_begin > ssst.class_end))
               and (not (tst.class_end < ssst.class_begin))) as x
    into time_conflict_flag;

    return time_conflict_flag;
end;
$$;


ALTER FUNCTION public.detect_conflict(stu_id integer, sec_id integer) OWNER TO whaleeye;

--
-- Name: drop_course(integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.drop_course(stu_id integer, sec_id integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare
    course_grade int;
begin
    select grade
    into course_grade
    from student_section_relations
    where student_id = stu_id
      and section_id = sec_id;
    if course_grade is null then
        delete from student_section_relations where student_id = stu_id and section_id = sec_id;
        update course_section set left_capacity = (left_capacity + 1) where id = sec_id;
        return true;
    else
        return false;
    end if;
end;
$$;


ALTER FUNCTION public.drop_course(stu_id integer, sec_id integer) OWNER TO whaleeye;

--
-- Name: enroll_course(integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.enroll_course(stu_id integer, sec_id integer) RETURNS integer
    LANGUAGE plpgsql
    AS $$
declare
    temp_course varchar;
    temp_bool   bool;
    left_cap    int;
begin
    -- NO STUDENT (UNKNOWN ERROR)
    if not exists(select null from student where id = stu_id) then
        return 0;
    end if;

    if not exists(select null from course_section where id = sec_id) then
        -- COURSE_NOT_FOUND
        return 1;
    end if;

    if exists(select grade
              from student_section_relations
              where student_id = stu_id
                and section_id = sec_id) then
        -- ALREADY_ENROLLED
        return 2;
    end if;

    select course_id, left_capacity
    into temp_course, left_cap
    from course_section
    where id = sec_id for update;

    if exists(select null
              from student_section_relations ssr
                       join course_section cs on cs.id = ssr.section_id
              where cs.course_id = temp_course
                and (ssr.grade = -1 or ssr.grade >= 60)
                and ssr.student_id = stu_id) then
        -- ALREADY_PASSED
        return 3;
    end if;

    select judge_prerequisite(stu_id, temp_course) into temp_bool;

    if not temp_bool then
        -- PREREQUISITE_NOT_FULFILLED
        return 4;
    end if;

    select detect_conflict(stu_id, sec_id) into temp_bool;
    if temp_bool then
        -- COURSE_CONFLICT_FOUND
        return 5;
    end if;


    if left_cap <= 0 then
        -- COURSE_IS_FULL
        return 6;
    end if;

    update course_section set left_capacity = (left_capacity - 1) where id = sec_id;
    insert into student_section_relations (student_id, section_id) values (stu_id, sec_id);
    -- SUCCESS
    return 7;

end;
$$;


ALTER FUNCTION public.enroll_course(stu_id integer, sec_id integer) OWNER TO whaleeye;

--
-- Name: generate_instructor_full_name(); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.generate_instructor_full_name() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if new.first_name ~ '[a-zA-Z ]' and new.last_name ~ '[a-zA-Z ]'
    then
        new.full_name := new.first_name || ' ' || new.last_name;
        new.other_name := new.first_name || new.last_name;
    else
        new.full_name := new.first_name || new.last_name;
        new.other_name := new.first_name || ' ' || new.last_name;
    end if;
    return new;
end;
$$;


ALTER FUNCTION public.generate_instructor_full_name() OWNER TO whaleeye;

--
-- Name: generate_section_full_name(); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.generate_section_full_name() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
declare
    course_name varchar;
begin
    select name into course_name from course where id = new.course_id;
    new.full_name := concat(course_name, '[', new.name, ']');
    return new;
end;
$$;


ALTER FUNCTION public.generate_section_full_name() OWNER TO whaleeye;

--
-- Name: generate_student_full_name(); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.generate_student_full_name() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
begin
    if new.first_name ~ '[a-zA-Z ]' and new.last_name ~ '[a-zA-Z ]'
    then
        new.full_name := new.first_name || ' ' || new.last_name;
    else
        new.full_name := new.first_name || new.last_name;
    end if;
    return new;
end;
$$;


ALTER FUNCTION public.generate_student_full_name() OWNER TO whaleeye;

--
-- Name: get_all_conflict_sections(integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_all_conflict_sections(stu_id integer, sem_id integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin

    drop table if exists student_selected_section_in_same_semester;
    create temp table if not exists student_selected_section_in_same_semester on commit drop as
    select section_id stu_sec_id
    from (select section_id
          from student_section_relations
          where student_id = stu_id
         ) as ssr
             join course_section cs on ssr.section_id = cs.id
    where cs.semester_id = sem_id;

    drop table if exists student_selected_section_time;
    create temp table if not exists student_selected_section_time on commit drop as
    select section_id, week_list, day_of_week, class_begin, class_end
    from course_section_class
    where section_id in (select stu_sec_id
                         from student_selected_section_in_same_semester);

    drop table if exists all_same_semester_section_time;
    create temp table if not exists all_same_semester_section_time on commit drop as
    select section_id, week_list, day_of_week, class_begin, class_end
    from course_section_class
    where section_id in (select id
                         from course_section cs
                         where cs.semester_id = sem_id);

-- Course Conflict contains itself
    return query
        select id as sec_id_course_conflict
        from course_section cs
        where cs.course_id in (
            select course_id
            from student_selected_section_in_same_semester as sssiss
                     join course_section as cs
                          on sssiss.stu_sec_id = cs.id
        )
          and semester_id = sem_id

        union

-- Time Conflict contains itself
        select assst.section_id
        from student_selected_section_time ssst
                 join all_same_semester_section_time assst
                      on ssst.week_list & assst.week_list > 0
                          and ssst.day_of_week = assst.day_of_week
        where (not (assst.class_begin > ssst.class_end))
          and (not (assst.class_end < ssst.class_begin));

end;
$$;


ALTER FUNCTION public.get_all_conflict_sections(stu_id integer, sem_id integer) OWNER TO whaleeye;

--
-- Name: get_all_users(); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_all_users() RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin
    return query
        select true,
               s.id,
               s.full_name,
               s.enrolled_date,
               m.id,
               m.name,
               d.id,
               d.name
        from student s
                 join major m on m.id = s.major_id
                 join department d on d.id = m.department_id
        union
        select false,
               id,
               full_name,
               null,
               null,
               null,
               null,
               null
        from instructor;
end;
$$;


ALTER FUNCTION public.get_all_users() OWNER TO whaleeye;

--
-- Name: get_course_section_classes(integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_course_section_classes(sec_id integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin
    if not exists(select null from course_section where id = sec_id) then
        return next null;
    end if;

    return query
        select csc.id,
               i.id,
               i.full_name,
               csc.day_of_week,
               csc.week_list,
               csc.class_begin,
               csc.class_end,
               csc.location
        from course_section_class csc
                 join instructor i on i.id = csc.instructor_id
        where csc.section_id = sec_id;
end;
$$;


ALTER FUNCTION public.get_course_section_classes(sec_id integer) OWNER TO whaleeye;

--
-- Name: get_course_sections_in_semester(character varying, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_course_sections_in_semester(cour_id character varying, sem_id integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin
    if not exists(select null from course where id = cour_id) or
       not exists(select null from semester where id = sem_id) then
        return next null;
    end if;

    return query select id, name, total_capacity, left_capacity
                 from course_section
                 where course_id = cour_id
                   and semester_id = sem_id;
end;
$$;


ALTER FUNCTION public.get_course_sections_in_semester(cour_id character varying, sem_id integer) OWNER TO whaleeye;

--
-- Name: get_enrolled_conflict_sections(integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_enrolled_conflict_sections(stu_id integer, sec_id integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin
    -- returns the sections' full names
-- order by full name

    drop table if exists student_selected_section_in_same_semester;
    create temp table if not exists student_selected_section_in_same_semester on commit drop as
    select cs.full_name, section_id stu_sec_id, course_id, semester_id
    from (select section_id
          from student_section_relations
          where student_id = stu_id
         ) as ssr
             join course_section cs on ssr.section_id = cs.id
    where cs.semester_id = (select semester_id
                            from course_section
                            where id = sec_id);

    drop table if exists student_selected_section_time;
    create temp table if not exists student_selected_section_time on commit drop as
    select full_name, week_list, day_of_week, class_begin, class_end
    from course_section_class
             join course_section on course_section_class.section_id = course_section.id
    where section_id in (select stu_sec_id
                         from student_selected_section_in_same_semester);

    drop table if exists target_section_time;
    create temp table if not exists target_section_time on commit drop as
    select week_list, day_of_week, class_begin, class_end
    from course_section_class
    where section_id = sec_id;

    return query
        select *
        from (
                 select full_name
                 from student_selected_section_in_same_semester
                 where course_id = (
                     select course_id
                     from course_section
                     where id = sec_id
                 )
                   and semester_id = (
                     select semester_id
                     from course_section
                     where id = sec_id
                 )
                 union
                 select ssst.full_name
                 from target_section_time tst
                          join student_selected_section_time ssst
                               on tst.week_list & ssst.week_list > 0
                                   and tst.day_of_week = ssst.day_of_week
                 where (not (tst.class_begin > ssst.class_end))
                   and (not (tst.class_end < ssst.class_begin))
             ) as whole_conflict_table
        order by full_name;

end;
$$;


ALTER FUNCTION public.get_enrolled_conflict_sections(stu_id integer, sec_id integer) OWNER TO whaleeye;

--
-- Name: get_enrolled_courses(integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_enrolled_courses(stu_id integer, sem_id integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin
    if not exists(select null from student where id = stu_id) then
        return next null;
    end if;
    if sem_id is not null then
        if not exists(select null from semester where id = sem_id) then
            return next null;
        end if;
    end if;

    return query select id, name, credit, class_hour, is_pf_grading, grade
                 from (select c.id,
                              c.name,
                              c.credit,
                              c.class_hour,
                              c.is_pf_grading,
                              ssr.grade,
                              row_number() over (partition by c.id order by s.begin_date desc) as rn
                       from student_section_relations ssr
                                join course_section cs on cs.id = ssr.section_id
                                join course c on c.id = cs.course_id
                                join semester s on s.id = cs.semester_id
                       where ssr.student_id = stu_id
                         and case sem_id is null
                                 when true then true
                                 when false then
                                     cs.semester_id = sem_id
                           end) x
                 where rn = 1;
end ;
$$;


ALTER FUNCTION public.get_enrolled_courses(stu_id integer, sem_id integer) OWNER TO whaleeye;

--
-- Name: get_enrolled_students_in_semester(character varying, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_enrolled_students_in_semester(cour_id character varying, sem_id integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin
    if not exists(select null from course where id = cour_id) or
       not exists(select null from semester where id = sem_id) then
        return next null;
    end if;

    return query
        select s.id, s.full_name, s.enrolled_date, m.id, m.name, d.id, d.name
        from course_section cs
                 join student_section_relations ssr on cs.id = ssr.section_id
                 join student s on s.id = ssr.student_id
                 join major m on m.id = s.major_id
                 join department d on d.id = m.department_id
        where cs.course_id = cour_id
          and cs.semester_id = sem_id;
end;
$$;


ALTER FUNCTION public.get_enrolled_students_in_semester(cour_id character varying, sem_id integer) OWNER TO whaleeye;

--
-- Name: get_instructed_course_sections(integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_instructed_course_sections(ins_id integer, sem_id integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin
    if not exists(select null from instructor where id = ins_id) or
       not exists(select null from semester where id = sem_id) then
        return next null;
    end if;

    return query
        select distinct cs.id, cs.name, cs.total_capacity, cs.left_capacity
        from course_section cs
                 join course_section_class csc on cs.id = csc.section_id
        where csc.instructor_id = ins_id
          and cs.semester_id = sem_id;
end;
$$;


ALTER FUNCTION public.get_instructed_course_sections(ins_id integer, sem_id integer) OWNER TO whaleeye;

--
-- Name: get_section_table(integer, date); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_section_table(i_stu_id integer, i_date date) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
declare
    start_date_of_cur_semester date;
    cur_semester_id            integer;
    week_num                   int;
    week_instance              int := 1;
begin

    select id, begin_date
    into cur_semester_id,
        start_date_of_cur_semester
    from semester
    where i_date >= semester.begin_date
      and i_date <= semester.end_date;

    select (((i_date - start_date_of_cur_semester) / 7) + 1) into week_num;
    week_instance = week_instance << (week_num - 1);

    return query
        select cs.full_name,
               i.id,
               i.full_name,
               csc.class_begin,
               csc.class_end,
               csc.location,
               csc.day_of_week
        from student_section_relations ssr
                 join course_section cs on ssr.section_id = cs.id
            and ssr.student_id = i_stu_id
            and cs.semester_id = cur_semester_id
                 join course_section_class csc on cs.id = csc.section_id
                 join instructor i on csc.instructor_id = i.id
        where csc.week_list & week_instance > 0
        order by day_of_week, class_begin;

end;
$$;


ALTER FUNCTION public.get_section_table(i_stu_id integer, i_date date) OWNER TO whaleeye;

--
-- Name: get_user(integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.get_user(user_id integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
begin
    return query
        select true,
               s.id,
               s.full_name,
               s.enrolled_date,
               m.id,
               m.name,
               d.id,
               d.name
        from student s
                 join major m on m.id = s.major_id
                 join department d on d.id = m.department_id
        where s.id = user_id
        union
        select false,
               id,
               full_name,
               null,
               null,
               null,
               null,
               null
        from instructor
        where id = user_id;
end;
$$;


ALTER FUNCTION public.get_user(user_id integer) OWNER TO whaleeye;

--
-- Name: judge_prerequisite(integer, character varying); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.judge_prerequisite(stu_id integer, cour_id character varying) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare
    pre   record;
    stack bool[];
    top   int := 1;
begin
    if not exists(select null from course where id = cour_id) or
       not exists(select null from student where id = stu_id) then
        return null;
    end if;
    drop table if exists selected;
    create temp table if not exists selected as
    select distinct x.id, x.and_logic, coalesce(y.rst, false) as rst
    from (select * from course_prerequisite_relations cpr where cpr.course_id = cour_id) x
             left join
         (select course_id, true as rst
          from student_section_relations ssr
                   join course_section cs on cs.id = ssr.section_id
          where ssr.student_id = stu_id
            and (ssr.grade >= 60 or ssr.grade = -1)) y
         on coalesce(x.prerequisite_id, '-1') = y.course_id;

    if not exists(select null from selected) then
        return true;
    end if;

    stack[1] = true;
    for pre in (select id, and_logic, rst from selected order by id)
        loop
            if pre.and_logic is null then
                stack[top] := pre.rst;
            elseif pre.and_logic then
                if top <= 2 then
                    top := top - 1;
                else
                    top := top - 2;
                    stack[top] := (stack[top] and stack[top + 1]);
                end if;
            else
                if top <= 2 then
                    top := top - 1;
                else
                    top := top - 2;
                    stack[top] := (stack[top] or stack[top + 1]);
                end if;
            end if;
            top := top + 1;
        end loop;
    drop table if exists selected;
    return stack[1];
end;
$$;


ALTER FUNCTION public.judge_prerequisite(stu_id integer, cour_id character varying) OWNER TO whaleeye;

--
-- Name: match_location(character varying, character varying[]); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.match_location(location character varying, locations character varying[]) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare
    temp int;
begin
    for temp in 1 .. array_length(locations, 1)
        loop
            if position(locations[temp] in location) > 0 then
                return true;
            end if;
        end loop;
    return false;
end ;
$$;


ALTER FUNCTION public.match_location(location character varying, locations character varying[]) OWNER TO whaleeye;

--
-- Name: remove_user(integer); Type: PROCEDURE; Schema: public; Owner: whaleeye
--

CREATE PROCEDURE public.remove_user(user_id integer)
    LANGUAGE plpgsql
    AS $$
begin
    delete from student where id = user_id;
    delete from instructor where id = user_id;
end;
$$;


ALTER PROCEDURE public.remove_user(user_id integer) OWNER TO whaleeye;

--
-- Name: search_course(integer, integer, character varying, character varying, character varying, integer, integer, character varying[], integer, boolean, boolean, boolean, boolean, integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.search_course(search_student_id integer, search_semester_id integer, search_course_id character varying, search_name character varying, search_instructor character varying, search_day_of_week integer, search_class_time integer, search_class_location character varying[], search_course_type integer, ignore_full boolean, ignore_conflict boolean, ignore_passed boolean, ignore_missing_prerequisites boolean, page_size integer, page_index integer) RETURNS SETOF record
    LANGUAGE plpgsql
    AS $$
declare
    empty_location bool := (array_length(search_class_location, 1) = 0);
begin
    return query
        with conflict_sections as (select *
                                   from get_all_conflict_sections(search_student_id,
                                                                  search_semester_id) as (section_id int))
        select available_sections.course_id,
               available_sections.course_name,
               available_sections.credit,
               available_sections.class_hour,
               available_sections.is_pf_grading,
               available_sections.section_id,
               available_sections.section_name,
               available_sections.total_capacity,
               available_sections.left_capacity,
               csc2.id,
               i2.id,
               i2.full_name as ins_full_name,
               csc2.day_of_week,
               csc2.week_list,
               csc2.class_begin,
               csc2.class_end,
               csc2.location
        from (select distinct c.id    as course_id,
                              c.name  as course_name,
                              c.credit,
                              c.class_hour,
                              c.is_pf_grading,
                              cs.id   as section_id,
                              cs.semester_id,
                              cs.name as section_name,
                              cs.full_name,
                              cs.total_capacity,
                              cs.left_capacity
              from course c
                       join course_section cs
                            on c.id = cs.course_id
                       join course_section_class csc on cs.id = csc.section_id
                       join semester s on s.id = cs.semester_id
                       join instructor i on csc.instructor_id = i.id
              where s.id = search_semester_id
                and case search_course_id is null
                        when true then true
                        when false then
                            position(search_course_id in c.id) > 0
                  end
                and case search_name is null
                        when true then true
                        when false then
                            position(search_name in cs.full_name) > 0
                  end
                and case search_instructor is null
                        when true then true
                        when false then (position(search_instructor in i.other_name) = 1
                            or position(search_instructor in i.last_name) = 1
                            or position(search_instructor in i.full_name) = 1)
                  end
                and case search_day_of_week is null
                        when true then true
                        when false then csc.day_of_week = search_day_of_week
                  end
                and case search_class_time is null
                        when true then true
                        when false then search_class_time between csc.class_begin
                            and csc.class_end
                  end
                and case (search_class_location is null or empty_location)
                        when true then true
                        when false then match_location(csc.location, search_class_location)
                  end
                and case search_course_type
                  -- ALL
                        when 1 then true
                  -- MAJOR_COMPULSORY
                        when 2 then c.id in (select course_id
                                             from major_course_relations mcr
                                                      join student s2 on mcr.major_id = s2.major_id
                                             where s2.id = search_student_id
                                               and mcr.is_compulsory)
                  -- MAJOR_ELECTIVE
                        when 3 then c.id in (select course_id
                                             from major_course_relations mcr
                                                      join student s2 on mcr.major_id = s2.major_id
                                             where s2.id = search_student_id
                                               and not mcr.is_compulsory)
                  -- CROSS_MAJOR
                        when 4 then c.id in (select distinct mcr.course_id
                                             from major_course_relations mcr
                                             where mcr.course_id not in (select course_id
                                                                         from major_course_relations mcr2
                                                                                  join student s3 on mcr2.major_id = s3.major_id
                                                                         where s3.id = search_student_id))
                  -- PUBLIC
                        when 5 then c.id not in
                                    (select distinct course_id from major_course_relations)
                  end
                and case ignore_full
                        when false then true
                        when true then cs.left_capacity > 0
                  end
                and case ignore_conflict
                        when false then true
                        when true
                            then cs.id not in (select section_id from conflict_sections)
                  end
                and case ignore_passed
                        when false then true
                        when true then c.id not in (select distinct cs1.course_id
                                                    from student_section_relations ssr
                                                             join course_section cs1 on cs1.id = ssr.section_id
                                                    where (ssr.grade = -1 or ssr.grade >= 60)
                                                      and ssr.student_id = search_student_id)
                  end
                and case ignore_missing_prerequisites
                        when false then true
                        when true then judge_prerequisite(search_student_id, c.id)
                  end
              order by course_id, cs.full_name
              limit page_size offset page_index * page_size) available_sections
                 join course_section_class csc2 on available_sections.section_id = csc2.section_id
                 join instructor i2 on i2.id = csc2.instructor_id
        order by course_id, available_sections.full_name;
end;
$$;


ALTER FUNCTION public.search_course(search_student_id integer, search_semester_id integer, search_course_id character varying, search_name character varying, search_instructor character varying, search_day_of_week integer, search_class_time integer, search_class_location character varying[], search_course_type integer, ignore_full boolean, ignore_conflict boolean, ignore_passed boolean, ignore_missing_prerequisites boolean, page_size integer, page_index integer) OWNER TO whaleeye;

--
-- Name: set_grade(integer, integer, integer); Type: FUNCTION; Schema: public; Owner: whaleeye
--

CREATE FUNCTION public.set_grade(stu_id integer, sec_id integer, grd integer) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
declare
    temp_bool bool;
begin
    if not exists(select null
                  from student_section_relations
                  where student_id = stu_id
                    and section_id = sec_id) then
        return false;
    end if;

    select c.is_pf_grading
    into temp_bool
    from course_section cs
             join course c on c.id = cs.course_id
    where cs.id = sec_id;

    if (temp_bool and grd >= 0) or (not temp_bool and grd < 0) then
        return false;
    end if;

    update student_section_relations
    set grade = grd
    where student_id = stu_id
      and section_id = sec_id;

    return true;

end;
$$;


ALTER FUNCTION public.set_grade(stu_id integer, sec_id integer, grd integer) OWNER TO whaleeye;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: course; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.course (
    id character varying NOT NULL,
    name character varying NOT NULL,
    credit integer NOT NULL,
    class_hour integer NOT NULL,
    is_pf_grading boolean NOT NULL
);


ALTER TABLE public.course OWNER TO whaleeye;

--
-- Name: course_prerequisite_relations; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.course_prerequisite_relations (
    id integer NOT NULL,
    course_id character varying NOT NULL,
    prerequisite_id character varying,
    and_logic boolean,
    CONSTRAINT prerequisite_or_logic CHECK ((((prerequisite_id IS NOT NULL) AND (and_logic IS NULL)) OR ((prerequisite_id IS NULL) AND (and_logic IS NOT NULL))))
);


ALTER TABLE public.course_prerequisite_relations OWNER TO whaleeye;

--
-- Name: course_prerequisite_relations_id_seq; Type: SEQUENCE; Schema: public; Owner: whaleeye
--

CREATE SEQUENCE public.course_prerequisite_relations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.course_prerequisite_relations_id_seq OWNER TO whaleeye;

--
-- Name: course_prerequisite_relations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: whaleeye
--

ALTER SEQUENCE public.course_prerequisite_relations_id_seq OWNED BY public.course_prerequisite_relations.id;


--
-- Name: course_section; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.course_section (
    id integer NOT NULL,
    course_id character varying NOT NULL,
    semester_id integer NOT NULL,
    name character varying NOT NULL,
    full_name character varying NOT NULL,
    total_capacity integer NOT NULL,
    left_capacity integer NOT NULL
);


ALTER TABLE public.course_section OWNER TO whaleeye;

--
-- Name: course_section_class; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.course_section_class (
    id integer NOT NULL,
    section_id integer NOT NULL,
    instructor_id integer NOT NULL,
    day_of_week integer NOT NULL,
    week_list integer NOT NULL,
    class_begin integer NOT NULL,
    class_end integer NOT NULL,
    location character varying NOT NULL
);


ALTER TABLE public.course_section_class OWNER TO whaleeye;

--
-- Name: course_section_class_id_seq; Type: SEQUENCE; Schema: public; Owner: whaleeye
--

CREATE SEQUENCE public.course_section_class_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.course_section_class_id_seq OWNER TO whaleeye;

--
-- Name: course_section_class_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: whaleeye
--

ALTER SEQUENCE public.course_section_class_id_seq OWNED BY public.course_section_class.id;


--
-- Name: course_section_id_seq; Type: SEQUENCE; Schema: public; Owner: whaleeye
--

CREATE SEQUENCE public.course_section_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.course_section_id_seq OWNER TO whaleeye;

--
-- Name: course_section_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: whaleeye
--

ALTER SEQUENCE public.course_section_id_seq OWNED BY public.course_section.id;


--
-- Name: department; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.department (
    id integer NOT NULL,
    name character varying NOT NULL
);


ALTER TABLE public.department OWNER TO whaleeye;

--
-- Name: department_id_seq; Type: SEQUENCE; Schema: public; Owner: whaleeye
--

CREATE SEQUENCE public.department_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.department_id_seq OWNER TO whaleeye;

--
-- Name: department_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: whaleeye
--

ALTER SEQUENCE public.department_id_seq OWNED BY public.department.id;


--
-- Name: instructor; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.instructor (
    id integer NOT NULL,
    first_name character varying NOT NULL,
    last_name character varying NOT NULL,
    full_name character varying NOT NULL,
    other_name character varying NOT NULL
);


ALTER TABLE public.instructor OWNER TO whaleeye;

--
-- Name: major; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.major (
    id integer NOT NULL,
    name character varying NOT NULL,
    department_id integer NOT NULL
);


ALTER TABLE public.major OWNER TO whaleeye;

--
-- Name: major_course_relations; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.major_course_relations (
    major_id integer NOT NULL,
    course_id character varying NOT NULL,
    is_compulsory boolean NOT NULL
);


ALTER TABLE public.major_course_relations OWNER TO whaleeye;

--
-- Name: major_id_seq; Type: SEQUENCE; Schema: public; Owner: whaleeye
--

CREATE SEQUENCE public.major_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.major_id_seq OWNER TO whaleeye;

--
-- Name: major_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: whaleeye
--

ALTER SEQUENCE public.major_id_seq OWNED BY public.major.id;


--
-- Name: semester; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.semester (
    id integer NOT NULL,
    name character varying NOT NULL,
    begin_date date NOT NULL,
    end_date date NOT NULL
);


ALTER TABLE public.semester OWNER TO whaleeye;

--
-- Name: semester_id_seq; Type: SEQUENCE; Schema: public; Owner: whaleeye
--

CREATE SEQUENCE public.semester_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.semester_id_seq OWNER TO whaleeye;

--
-- Name: semester_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: whaleeye
--

ALTER SEQUENCE public.semester_id_seq OWNED BY public.semester.id;


--
-- Name: student; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.student (
    id integer NOT NULL,
    first_name character varying NOT NULL,
    last_name character varying NOT NULL,
    full_name character varying NOT NULL,
    enrolled_date date NOT NULL,
    major_id integer NOT NULL
);


ALTER TABLE public.student OWNER TO whaleeye;

--
-- Name: student_section_relations; Type: TABLE; Schema: public; Owner: whaleeye
--

CREATE TABLE public.student_section_relations (
    student_id integer NOT NULL,
    section_id integer NOT NULL,
    grade integer
);


ALTER TABLE public.student_section_relations OWNER TO whaleeye;

--
-- Name: course_prerequisite_relations id; Type: DEFAULT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_prerequisite_relations ALTER COLUMN id SET DEFAULT nextval('public.course_prerequisite_relations_id_seq'::regclass);


--
-- Name: course_section id; Type: DEFAULT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_section ALTER COLUMN id SET DEFAULT nextval('public.course_section_id_seq'::regclass);


--
-- Name: course_section_class id; Type: DEFAULT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_section_class ALTER COLUMN id SET DEFAULT nextval('public.course_section_class_id_seq'::regclass);


--
-- Name: department id; Type: DEFAULT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.department ALTER COLUMN id SET DEFAULT nextval('public.department_id_seq'::regclass);


--
-- Name: major id; Type: DEFAULT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.major ALTER COLUMN id SET DEFAULT nextval('public.major_id_seq'::regclass);


--
-- Name: semester id; Type: DEFAULT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.semester ALTER COLUMN id SET DEFAULT nextval('public.semester_id_seq'::regclass);


--
-- Name: major_course_relations course_major_primary_key; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.major_course_relations
    ADD CONSTRAINT course_major_primary_key PRIMARY KEY (major_id, course_id);


--
-- Name: course course_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_pkey PRIMARY KEY (id);


--
-- Name: course_prerequisite_relations course_prerequisite_relations_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_prerequisite_relations
    ADD CONSTRAINT course_prerequisite_relations_pkey PRIMARY KEY (id);


--
-- Name: course_section_class course_section_class_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_section_class
    ADD CONSTRAINT course_section_class_pkey PRIMARY KEY (id);


--
-- Name: course_section course_section_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_section
    ADD CONSTRAINT course_section_pkey PRIMARY KEY (id);


--
-- Name: department department_name_key; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT department_name_key UNIQUE (name);


--
-- Name: department department_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.department
    ADD CONSTRAINT department_pkey PRIMARY KEY (id);


--
-- Name: instructor instructor_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.instructor
    ADD CONSTRAINT instructor_pkey PRIMARY KEY (id);


--
-- Name: major major_name_key; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.major
    ADD CONSTRAINT major_name_key UNIQUE (name);


--
-- Name: major major_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.major
    ADD CONSTRAINT major_pkey PRIMARY KEY (id);


--
-- Name: semester semester_name_key; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.semester
    ADD CONSTRAINT semester_name_key UNIQUE (name);


--
-- Name: semester semester_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.semester
    ADD CONSTRAINT semester_pkey PRIMARY KEY (id);


--
-- Name: student student_pkey; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.student
    ADD CONSTRAINT student_pkey PRIMARY KEY (id);


--
-- Name: student_section_relations student_section_primary_key; Type: CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.student_section_relations
    ADD CONSTRAINT student_section_primary_key PRIMARY KEY (student_id, section_id);


--
-- Name: idx_coursesection_courseidsemesterid; Type: INDEX; Schema: public; Owner: whaleeye
--

CREATE INDEX idx_coursesection_courseidsemesterid ON public.course_section USING btree (course_id, semester_id);


--
-- Name: idx_coursesectionclass_instructorid; Type: INDEX; Schema: public; Owner: whaleeye
--

CREATE INDEX idx_coursesectionclass_instructorid ON public.course_section_class USING btree (instructor_id);


--
-- Name: idx_coursesectionclass_sectionid; Type: INDEX; Schema: public; Owner: whaleeye
--

CREATE INDEX idx_coursesectionclass_sectionid ON public.course_section_class USING btree (section_id);


--
-- Name: instructor update_instructor_full_name; Type: TRIGGER; Schema: public; Owner: whaleeye
--

CREATE TRIGGER update_instructor_full_name BEFORE INSERT OR UPDATE ON public.instructor FOR EACH ROW EXECUTE FUNCTION public.generate_instructor_full_name();


--
-- Name: course_section update_section_full_name; Type: TRIGGER; Schema: public; Owner: whaleeye
--

CREATE TRIGGER update_section_full_name BEFORE INSERT OR UPDATE ON public.course_section FOR EACH ROW EXECUTE FUNCTION public.generate_section_full_name();


--
-- Name: student update_student_full_name; Type: TRIGGER; Schema: public; Owner: whaleeye
--

CREATE TRIGGER update_student_full_name BEFORE INSERT OR UPDATE ON public.student FOR EACH ROW EXECUTE FUNCTION public.generate_student_full_name();


--
-- Name: course_prerequisite_relations course_prerequisite_relations_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_prerequisite_relations
    ADD CONSTRAINT course_prerequisite_relations_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;


--
-- Name: course_prerequisite_relations course_prerequisite_relations_prerequisite_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_prerequisite_relations
    ADD CONSTRAINT course_prerequisite_relations_prerequisite_id_fkey FOREIGN KEY (prerequisite_id) REFERENCES public.course(id) ON DELETE CASCADE;


--
-- Name: course_section_class course_section_class_instructor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_section_class
    ADD CONSTRAINT course_section_class_instructor_id_fkey FOREIGN KEY (instructor_id) REFERENCES public.instructor(id) ON DELETE CASCADE;


--
-- Name: course_section_class course_section_class_section_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_section_class
    ADD CONSTRAINT course_section_class_section_id_fkey FOREIGN KEY (section_id) REFERENCES public.course_section(id) ON DELETE CASCADE;


--
-- Name: course_section course_section_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_section
    ADD CONSTRAINT course_section_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;


--
-- Name: course_section course_section_semester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.course_section
    ADD CONSTRAINT course_section_semester_id_fkey FOREIGN KEY (semester_id) REFERENCES public.semester(id) ON DELETE CASCADE;


--
-- Name: major_course_relations major_course_relations_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.major_course_relations
    ADD CONSTRAINT major_course_relations_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;


--
-- Name: major_course_relations major_course_relations_major_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.major_course_relations
    ADD CONSTRAINT major_course_relations_major_id_fkey FOREIGN KEY (major_id) REFERENCES public.major(id) ON DELETE CASCADE;


--
-- Name: major major_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.major
    ADD CONSTRAINT major_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.department(id) ON DELETE CASCADE;


--
-- Name: student student_major_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.student
    ADD CONSTRAINT student_major_id_fkey FOREIGN KEY (major_id) REFERENCES public.major(id) ON DELETE CASCADE;


--
-- Name: student_section_relations student_section_relations_section_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.student_section_relations
    ADD CONSTRAINT student_section_relations_section_id_fkey FOREIGN KEY (section_id) REFERENCES public.course_section(id) ON DELETE CASCADE;


--
-- Name: student_section_relations student_section_relations_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: whaleeye
--

ALTER TABLE ONLY public.student_section_relations
    ADD CONSTRAINT student_section_relations_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.student(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

