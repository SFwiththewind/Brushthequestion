package mapper;

import entity.Questions;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface QuestionMapper {
    List<Questions> selectByUserId(Integer userId);

    void insert(Questions question);

    @Select("SELECT * FROM questions WHERE file_id = #{fileId} AND user_id = #{userId}")
    List<Questions> selectByFileIdAndUserId(@Param("fileId") Integer fileId, @Param("userId") Integer userId);

}
