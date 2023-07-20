package com.example.motiondetection3;

public class FrameData {

    String ImgName,ImgUrl,Latitude,Longitude,DateTime;

    public FrameData() {
    }

    public FrameData(String imgName, String imgUrl, String latitude, String longitude, String dateTime) {
        ImgName = imgName;
        ImgUrl = imgUrl;
        Latitude = latitude;
        Longitude = longitude;
        DateTime = dateTime;
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
}
