package com.example.motiondetection3;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class FrameData {

    String ImgName,ImgUrl,Latitude,Longitude,DateTime;
    List<String> Classes=new ArrayList<String>();

    public FrameData(String imgName, String imgUrl, String latitude, String longitude, String dateTime, List<String> classes) {
        ImgName = imgName;
        ImgUrl = imgUrl;
        Latitude = latitude;
        Longitude = longitude;
        DateTime = dateTime;
        Classes = classes;
    }

    public String getImgName() {
        return ImgName;
    }

    public void setImgName(String imgName) {
        ImgName = imgName;
    }

    public String getImgUrl() {
        return ImgUrl;
    }

    public void setImgUrl(String imgUrl) {
        ImgUrl = imgUrl;
    }

    public String getLatitude() {
        return Latitude;
    }

    public void setLatitude(String latitude) {
        Latitude = latitude;
    }

    public String getLongitude() {
        return Longitude;
    }

    public void setLongitude(String longitude) {
        Longitude = longitude;
    }

    public String getDateTime() {
        return DateTime;
    }

    public void setDateTime(String dateTime) {
        DateTime = dateTime;
    }

    public List<String> getClasses(){return Classes;}

    public void setClasses(List<String> classes){Classes = classes;}

}
