package org.una.programmingIII.UTEMP_Project.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.una.programmingIII.UTEMP_Project.models.FileMetadatum;


public interface FileMetadatumRepository extends JpaRepository<FileMetadatum, Long> {

    /**
     * Whether the user uploaded the file or teaches the course its submission belongs to.
     */
    @Query("""
            select count(f) > 0 from FileMetadatum f
            left join f.submission s
            left join s.assignment a
            left join a.course c
            where f.id = :fileId and (f.student.id = :userId or c.teacher.id = :userId)
            """)
    boolean isOwnerOrCourseTeacher(@Param("fileId") Long fileId, @Param("userId") Long userId);

    @Query("select count(f) > 0 from FileMetadatum f where f.id = :fileId and f.student.id = :userId")
    boolean isOwner(@Param("fileId") Long fileId, @Param("userId") Long userId);
}
