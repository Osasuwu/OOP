package services.importer.file;

import models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.importer.ImportException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * Implementation of FileImportAdapter for XML file format.
 * Handles XML files containing music data.
 */
public class XmlFileImportAdapter implements FileImportAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlFileImportAdapter.class);
    private static final String[] SUPPORTED_EXTENSIONS = {".xml"};
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        LOGGER.info("Starting XML import from file: {}", filePath);
        
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();
            
            UserMusicData userData = new UserMusicData();
            
            // Process XML structure - this is a simplified example
            NodeList songNodes = document.getElementsByTagName("song");
            for (int i = 0; i < songNodes.getLength(); i++) {
                Node songNode = songNodes.item(i);
                
                if (songNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element songElement = (Element) songNode;
                    
                    String title = getElementValue(songElement, "title");
                    String artistName = getElementValue(songElement, "artist");
                    
                    if (title != null && artistName != null) {
                        // Create artist
                        Artist artist = userData.findOrCreateArtist(artistName);
                        
                        // Create song
                        Song song = new Song(title, artistName);
                        song.setArtist(artist);
                        
                        // Add album if available
                        String album = getElementValue(songElement, "album");
                        if (album != null) {
                            song.setAlbumName(album);
                        }
                        
                        // Add song to user data
                        userData.addSong(song);
                        
                        // Create play history if timestamp exists
                        String timestamp = getElementValue(songElement, "played_at");
                        if (timestamp != null) {
                            try {
                                Date playDate = new Date(Long.parseLong(timestamp));
                                userData.addPlayHistory(new PlayHistory(song, playDate));
                            } catch (NumberFormatException e) {
                                LOGGER.warn("Invalid timestamp format: {}", timestamp);
                            }
                        }
                    }
                }
            }
            
            LOGGER.info("XML import completed. Found {} songs, {} artists",
                      userData.getSongs().size(), userData.getArtists().size());
            
            return userData;
        } catch (IOException e) {
            LOGGER.error("Failed to read XML file: {}", filePath, e);
            throw new ImportException("Failed to read XML file: " + e.getMessage(), e);
        } catch (ParserConfigurationException | SAXException e) {
            LOGGER.error("Failed to parse XML file: {}", filePath, e);
            throw new ImportException("Failed to parse XML file: " + e.getMessage(), e);
        }
    }
    
    private String getElementValue(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return node.getTextContent();
            }
        }
        return null;
    }
    
    @Override
    public boolean canHandle(Path filePath) {
        if (filePath == null) return false;
        
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
