package com.asanni.dashboard.models;

import com.google.cloud.firestore.GeoPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestaurantOwner {
    private boolean acceptingDiners;
    private String address;
    private String areaId;
    private long availabilityUpdatedAt;
    private long availableTables;
    private String cityId;
    private String coverUrl;
    private List<String> cuisines;
    private boolean isEnabled;
    private boolean isOpen;
    private GeoPoint location;
    private String logoUrl;
    private String name;
    private boolean perHead;
    private String phone;
    private long price;
    private String selectedPlan;
    private long totalTables;
    private long waitMinutes;

    // Empty constructor required by Firestore
    public RestaurantOwner() {}

    public RestaurantOwner(String name, String address, String cityId, String areaId, String phone,
                           String logoUrl, String coverUrl, List<String> cuisines, long totalTables,
                           long availableTables, boolean perHead, long price, String selectedPlan,
                           GeoPoint location) {
        this.name = name;
        this.address = address;
        this.cityId = cityId;
        this.areaId = areaId;
        this.phone = phone;
        this.logoUrl = logoUrl;
        this.coverUrl = coverUrl;
        this.cuisines = cuisines;
        this.totalTables = totalTables;
        this.availableTables = availableTables;
        this.perHead = perHead;
        this.price = price;
        this.selectedPlan = selectedPlan;
        this.location = location;
        this.acceptingDiners = true;
        this.isEnabled = true;
        this.isOpen = true;
        this.availabilityUpdatedAt = System.currentTimeMillis();
        this.waitMinutes = 0;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("acceptingDiners", acceptingDiners);
        map.put("address", address);
        map.put("areaId", areaId);
        map.put("availabilityUpdatedAt", availabilityUpdatedAt);
        map.put("availableTables", availableTables);
        map.put("cityId", cityId);
        map.put("coverUrl", coverUrl);
        map.put("cuisines", cuisines);
        map.put("isEnabled", isEnabled);
        map.put("isOpen", isOpen);
        map.put("location", location);
        map.put("logoUrl", logoUrl);
        map.put("name", name);
        map.put("perHead", perHead);
        map.put("phone", phone);
        map.put("price", price);
        map.put("selectedPlan", selectedPlan);
        map.put("totalTables", totalTables);
        map.put("waitMinutes", waitMinutes);
        return map;
    }

    // Getters & Setters
    public boolean isAcceptingDiners() { return acceptingDiners; }
    public void setAcceptingDiners(boolean acceptingDiners) { this.acceptingDiners = acceptingDiners; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getAreaId() { return areaId; }
    public void setAreaId(String areaId) { this.areaId = areaId; }

    public long getAvailabilityUpdatedAt() { return availabilityUpdatedAt; }
    public void setAvailabilityUpdatedAt(long availabilityUpdatedAt) { this.availabilityUpdatedAt = availabilityUpdatedAt; }

    public long getAvailableTables() { return availableTables; }
    public void setAvailableTables(long availableTables) { this.availableTables = availableTables; }

    public String getCityId() { return cityId; }
    public void setCityId(String cityId) { this.cityId = cityId; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public List<String> getCuisines() { return cuisines; }
    public void setCuisines(List<String> cuisines) { this.cuisines = cuisines; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean open) { isOpen = open; }

    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isPerHead() { return perHead; }
    public void setPerHead(boolean perHead) { this.perHead = perHead; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public String getSelectedPlan() { return selectedPlan; }
    public void setSelectedPlan(String selectedPlan) { this.selectedPlan = selectedPlan; }

    public long getTotalTables() { return totalTables; }
    public void setTotalTables(long totalTables) { this.totalTables = totalTables; }

    public long getWaitMinutes() { return waitMinutes; }
    public void setWaitMinutes(long waitMinutes) { this.waitMinutes = waitMinutes; }
}