package czm.record;

public class DestroyEvent {
    private String message;

    public DestroyEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
