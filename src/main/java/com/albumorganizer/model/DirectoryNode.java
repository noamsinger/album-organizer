package com.albumorganizer.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tree structure for directory hierarchy in left panel.
 */
public class DirectoryNode {
    private Path path;
    private String displayName;
    private List<DirectoryNode> children;
    private boolean scanned;
    private int mediaFileCount; // Direct files in this directory only
    private int recursiveFileCount; // Total files in this directory and all subdirectories
    private boolean album; // True if this is a base/album folder

    public DirectoryNode(Path path) {
        this.path = path;
        this.displayName = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        this.children = new ArrayList<>();
        this.scanned = false;
        this.mediaFileCount = 0;
        this.recursiveFileCount = 0;
        this.album = false;
    }

    public DirectoryNode(Path path, String displayName) {
        this.path = path;
        this.displayName = displayName;
        this.children = new ArrayList<>();
        this.scanned = false;
        this.mediaFileCount = 0;
        this.recursiveFileCount = 0;
        this.album = false;
    }

    public DirectoryNode(Path path, String displayName, boolean album) {
        this.path = path;
        this.displayName = displayName;
        this.children = new ArrayList<>();
        this.scanned = false;
        this.mediaFileCount = 0;
        this.album = album;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<DirectoryNode> getChildren() {
        return children;
    }

    public void setChildren(List<DirectoryNode> children) {
        this.children = children;
    }

    public void addChild(DirectoryNode child) {
        this.children.add(child);
    }

    public boolean isScanned() {
        return scanned;
    }

    public void setScanned(boolean scanned) {
        this.scanned = scanned;
    }

    public int getMediaFileCount() {
        return mediaFileCount;
    }

    public void setMediaFileCount(int mediaFileCount) {
        this.mediaFileCount = mediaFileCount;
    }

    public int getRecursiveFileCount() {
        return recursiveFileCount;
    }

    public void setRecursiveFileCount(int recursiveFileCount) {
        this.recursiveFileCount = recursiveFileCount;
    }

    public boolean isAlbum() {
        return album;
    }

    public void setAlbum(boolean album) {
        this.album = album;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectoryNode that = (DirectoryNode) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        if (scanned) {
            return displayName + " (" + mediaFileCount + "/" + recursiveFileCount + ")";
        }
        return displayName;
    }
}
