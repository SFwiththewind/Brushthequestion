package untils;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class Result {
    private Integer code;
    private String message;
    private Map<String, Object> data = new HashMap<>();
    private Long timestamp = System.currentTimeMillis();

    // 私有构造器
    private Result() {}


    public static Result ok() {
        Result result = new Result();
        result.setCode(200);
        result.setMessage("Success");
        return result;
    }


    public static Result error(Integer code, String message) {
        Result result = new Result();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }


    public Result data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }


    public static Result ok(Map<String, Object> data) {
        Result result = ok();
        result.setData(data);
        return result;
    }

    public Result msg(String message){
        this.setMessage(message);
        return this;
    }
}