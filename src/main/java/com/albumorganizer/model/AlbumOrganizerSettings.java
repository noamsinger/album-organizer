package com.albumorganizer.model;

import java.nio.file.Path;

/**
 * Settings for organizing media files.
 */
public class AlbumOrganizerSettings {

    public enum OrganizeMode {
        COPY, MOVE
    }

    private OrganizeMode mode;
    private boolean createYearFolder;
    private boolean createMonthFolder;
    private boolean createDayFolder;
    private boolean splitLowRes;
    private boolean splitMedRes;
    private int lowResThresholdPixels;
    private int hiResThresholdPixels;
    private Path targetFolder; // The designated target folder
    private int fontSizeFactor; // Font size factor for scaling (0 = 100%)

    public AlbumOrganizerSettings() {
        // Defaults
        this.mode = OrganizeMode.COPY;
        this.createYearFolder = true;
        this.createMonthFolder = true;
        this.createDayFolder = true;
        this.splitLowRes = true;
        this.splitMedRes = true;
        this.lowResThresholdPixels = 300000;
        this.hiResThresholdPixels = 1000000;
        this.targetFolder = null;
        this.fontSizeFactor = 0;
    }

    public OrganizeMode getMode() {
        return mode;
    }

    public void setMode(OrganizeMode mode) {
        this.mode = mode;
    }

    public boolean isCreateYearFolder() {
        return createYearFolder;
    }

    public void setCreateYearFolder(boolean createYearFolder) {
        this.createYearFolder = createYearFolder;
    }

    public boolean isCreateMonthFolder() {
        return createMonthFolder;
    }

    public void setCreateMonthFolder(boolean createMonthFolder) {
        this.createMonthFolder = createMonthFolder;
    }

    public boolean isCreateDayFolder() {
        return createDayFolder;
    }

    public void setCreateDayFolder(boolean createDayFolder) {
        this.createDayFolder = createDayFolder;
    }

    public boolean isSplitLowRes() {
        return splitLowRes;
    }

    public void setSplitLowRes(boolean splitLowRes) {
        this.splitLowRes = splitLowRes;
    }

    public boolean isSplitMedRes() {
        return splitMedRes;
    }

    public void setSplitMedRes(boolean splitMedRes) {
        this.splitMedRes = splitMedRes;
    }

    public int getLowResThresholdPixels() {
        return lowResThresholdPixels;
    }

    public void setLowResThresholdPixels(int lowResThresholdPixels) {
        this.lowResThresholdPixels = lowResThresholdPixels;
    }

    public int getHiResThresholdPixels() {
        return hiResThresholdPixels;
    }

    public void setHiResThresholdPixels(int hiResThresholdPixels) {
        this.hiResThresholdPixels = hiResThresholdPixels;
    }

    public Path getTargetFolder() {
        return targetFolder;
    }

    public void setTargetFolder(Path targetFolder) {
        this.targetFolder = targetFolder;
    }

    public int getFontSizeFactor() {
        return fontSizeFactor;
    }

    public void setFontSizeFactor(int fontSizeFactor) {
        this.fontSizeFactor = fontSizeFactor;
    }
}
