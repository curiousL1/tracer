package czm.record;

public class UsageEvent {

    public int flag; //0 == alarm , 1 == pause

    public UsageEvent(int flag) {
        this.flag = flag;
    }

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }
}
