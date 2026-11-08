package com.asanni.dashboard.models;

import com.google.cloud.firestore.GeoPoint;
import java.util.ArrayList;
import java.util.List;

public class OnboardingData {
    private static OnboardingData instance;

    private String selectedPlan;
    private String restaurantName;
    private String primaryCuisine;
    private String city;
    private String cityId;
    private String area;
    private String areaId;
    private String address;
    private String phone;
    private String logoUrl;
    private int numberOfTables;
    private boolean perHead;
    private long perHeadPrice;
    private String coverUrl;
    private String shortDescription;
    private GeoPoint location;
    private List<String> cuisines;

    private OnboardingData() {
        this.cuisines = new ArrayList<>();
    }

    public static synchronized OnboardingData getInstance() {
        if (instance == null) {
            instance = new OnboardingData();
        }
        return instance;
    }

    public static synchronized void reset() {
        instance = new OnboardingData();
    }

    // Getters and Setters
    public String getSelectedPlan() { return selectedPlan; }
    public void setSelectedPlan(String selectedPlan) { this.selectedPlan = selectedPlan; }

    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }

    public String getPrimaryCuisine() { return primaryCuisine; }
    public void setPrimaryCuisine(String primaryCuisine) { 
        this.primaryCuisine = primaryCuisine;
        if (!cuisines.contains(primaryCuisine)) {
            cuisines.add(primaryCuisine);
        }
    }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCityId() { return cityId; }
    public void setCityId(String cityId) { this.cityId = cityId; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public int getNumberOfTables() { return numberOfTables; }
    public void setNumberOfTables(int numberOfTables) { this.numberOfTables = numberOfTables; }

    public boolean isPerHead() { return perHead; }
    public void setPerHead(boolean perHead) { this.perHead = perHead; }

    public long getPerHeadPrice() { return perHeadPrice; }
    public void setPerHeadPrice(long perHeadPrice) { this.perHeadPrice = perHeadPrice; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }

    public List<String> getCuisines() { return cuisines; }
    public void setCuisines(List<String> cuisines) { this.cuisines = cuisines; }

    public void addCuisine(String cuisine) {
        if (!cuisines.contains(cuisine)) {
            cuisines.add(cuisine);
        }
    }

    public RestaurantOwner toRestaurantOwner() {
        return new RestaurantOwner(
            restaurantName,
            address,
            cityId,
            areaId,
            phone,
            logoUrl,
            coverUrl,
            cuisines,
            numberOfTables,
            numberOfTables,
            perHead,
            perHead ? perHeadPrice : 0,
            selectedPlan,
            location
        );
    }
}
