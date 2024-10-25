package com.plantssoil.common.persistence.rdbms;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import javax.persistence.TypedQuery;

import com.plantssoil.common.persistence.IPersistence;
import com.plantssoil.common.persistence.IPersistenceFactory;
import com.plantssoil.common.persistence.rdbms.beans.Address;
import com.plantssoil.common.persistence.rdbms.beans.Course;
import com.plantssoil.common.persistence.rdbms.beans.Student;
import com.plantssoil.common.persistence.rdbms.beans.Teacher;

public class JPAPersistenceTestUtil {
    private Teacher newTeacherEntity() {
        Teacher teacher = new Teacher();
        teacher.setTeacherId(UUID.randomUUID().toString());
        teacher.setTeacherName("Teacher " + ThreadLocalRandom.current().nextInt());
        return teacher;
    }

    private Course newCourseEntity() {
        Course course = new Course();
        course.setCourseId(UUID.randomUUID().toString());
        course.setCourseName("Course" + ThreadLocalRandom.current().nextInt());
        return course;
    }

    private Student newStudentEntity() {
        Student student = new Student();
        student.setStudentId(UUID.randomUUID().toString());
        student.setStudentName("Student" + ThreadLocalRandom.current().nextInt());
        student.setGender(Student.Gender.Female);
        student.setAddress(new Address("Address No " + ThreadLocalRandom.current().nextInt(), "Street " + ThreadLocalRandom.current().nextInt(),
                "City " + ThreadLocalRandom.current().nextInt(), "Province " + ThreadLocalRandom.current().nextInt()));
        student.setCreationTime(new Date());
        for (int i = 0; i < 3; i++) {
            student.getCourses().add(newCourseEntity());
        }
        return student;
    }

    protected void testCreate(IPersistenceFactory factory) {
        try {
            ExecutorService ess = Executors.newFixedThreadPool(20);
            List<Future<Teacher>> futures = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                futures.add(ess.submit(() -> {
                    Teacher teacher = newTeacherEntity();
                    try (IPersistence persists = factory.create()) {
                        persists.create(teacher);
                        TypedQuery<Teacher> query = ((JPAPersistence) persists).getEntityManager().createQuery("SELECT t FROM Teacher t WHERE t.teacherId = ?1",
                                Teacher.class);
                        query.setParameter(1, teacher.getTeacherId());
                        Teacher updated = query.getSingleResult();
                        System.out.println(updated);
                        updated.setTeacherName("DDDDDDDDDDDD");
                        updated = persists.update(updated);
                        updated = query.getSingleResult();
                        return updated;
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail(e.getMessage());
                        return null;
                    }
                }));
            }
            ess.shutdown();

            for (Future<Teacher> future : futures) {
                Teacher teacher = future.get();
                System.out.println(teacher);
                assertNotNull(teacher.getTeacherId());
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    protected void testCreateList(IPersistenceFactory factory) {
        try {
            ExecutorService ess = Executors.newFixedThreadPool(20);
            List<Future<Student>> futures = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                futures.add(ess.submit(() -> {
                    Student student = newStudentEntity();
                    List<Object> entities = new ArrayList<>();
                    entities.add(student);
                    for (Course course : student.getCourses()) {
                        entities.add(course);
                    }

                    try (IPersistence persists = factory.create()) {
                        persists.create(entities);
                        return student;
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail(e.getMessage());
                        return null;
                    }
                }));
            }
            ess.shutdown();

            for (Future<Student> future : futures) {
                Student student = future.get();
//                System.out.println(student);
                assertNotNull(student.getStudentId());
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    protected void testUpdate(IPersistenceFactory factory) {
        List<Teacher> ts = null;
        try (JPAPersistence persists = (JPAPersistence) factory.create()) {
            TypedQuery<Teacher> q = persists.getEntityManager().createQuery("SELECT t FROM Teacher t", Teacher.class).setFirstResult(0).setMaxResults(5);
            ts = q.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        final int teacherCount = ts.size();
        ExecutorService ess = Executors.newFixedThreadPool(5);
        List<Future<Teacher>> futures = new ArrayList<>();
        for (Teacher t : ts) {
            futures.add(ess.submit(() -> {
                t.setTeacherName("Teacher" + ThreadLocalRandom.current().nextInt(teacherCount));
                try (IPersistence p = factory.create()) {
                    Teacher updated = p.update(t);
                    return updated;
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                    return null;
                }
            }));
        }
        ess.shutdown();

        for (Future<Teacher> future : futures) {
            Teacher teacher = null;
            try {
                teacher = future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
//            System.out.print(teacher);
            assertNotNull(teacher.getTeacherId());
        }
    }

    protected void testUpdateList(IPersistenceFactory factory) {
        List<Student> ts = null;
        try (JPAPersistence persists = (JPAPersistence) factory.create()) {
            TypedQuery<Student> q = persists.getEntityManager().createQuery("SELECT t FROM Student t", Student.class).setFirstResult(0).setMaxResults(5);
            ts = q.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        ExecutorService ess = Executors.newFixedThreadPool(20);
        List<Future<Student>> futures = new ArrayList<>();
        for (Student t : ts) {
            futures.add(ess.submit(() -> {
                List<Object> entities = new ArrayList<>();
                t.setStudentName("Student" + ThreadLocalRandom.current().nextInt(20));
                entities.add(t);
                for (Course course : t.getCourses()) {
                    course.setCourseName("Course" + ThreadLocalRandom.current().nextInt(20));
                    entities.add(course);
                }
                try (IPersistence p = factory.create()) {
                    List<Object> updated = p.update(entities);
                    return (Student) updated.get(0);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                    return null;
                }
            }));
        }
        ess.shutdown();

        for (Future<Student> future : futures) {
            Student student = null;
            try {
                student = future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
//            System.out.println(student);
            assertNotNull(student.getStudentId());
        }
    }

    protected void testRemove(IPersistenceFactory factory) {
        List<Teacher> ts = null;
        try (JPAPersistence persists = (JPAPersistence) factory.create()) {
            TypedQuery<Teacher> q = persists.getEntityManager().createQuery("SELECT t FROM Teacher t", Teacher.class).setFirstResult(0).setMaxResults(5);
            ts = q.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        ExecutorService ess = Executors.newFixedThreadPool(20);
        List<Future<Teacher>> futures = new ArrayList<>();
        for (Teacher t : ts) {
            futures.add(ess.submit(() -> {
                try (IPersistence p = factory.create()) {
                    p.remove(t);
                    return t;
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                    return null;
                }
            }));
        }
        ess.shutdown();

        for (Future<Teacher> future : futures) {
            Teacher teacher = null;
            try {
                teacher = future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
//            System.out.println("Removed: " + teacher);
            assertNotNull(teacher.getTeacherId());
        }
    }

    protected void testRemoveList(IPersistenceFactory factory) {
        List<Student> ts = null;
        try (JPAPersistence persists = (JPAPersistence) factory.create()) {
            TypedQuery<Student> q = persists.getEntityManager().createQuery("SELECT t FROM Student t", Student.class).setFirstResult(0).setMaxResults(5);
            ts = q.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        ExecutorService ess = Executors.newFixedThreadPool(20);
        for (Student t : ts) {
            ess.execute(() -> {
                List<Object> entities = new ArrayList<>();
                for (Course course : t.getCourses()) {
                    entities.add(course);
                }
                entities.add(t);
                try (IPersistence p = factory.create()) {
                    p.remove(entities);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            });
        }
        ess.shutdown();
        assertTrue(true);
    }

}