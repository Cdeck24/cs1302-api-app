package cs1302.api;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import com.google.gson.Gson;

import java.net.http.*;
import java.net.URI;

import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 * The {@code ApiApp} class is a JavaFX application that provides
 * movie information, including streaming availability, by utilizing
 * OMDB and WatchMode APIs.
 */
public class ApiApp extends Application {

    // Declare UI elements
    Stage stage;
    Scene scene;
    VBox root;
    HBox contentBox;
    VBox movieDetailsVBox;
    VBox streamingInfoVBox;
    TextFlow movieDetailsFlow;
    ImageView posterImageView;
    TextField searchField;
    Button searchButton;
    Label streamingInfoLabel;

    // API keys for OMDB and WatchMode services
    private static final String OMDB_API_KEY = "95c8a566";
    private static final String WATCHMODE_API_KEY = "ooDknqZZ8bPZHQwGJH4XHffdemFaw48553tdgE2Y";

    /**
     * Constructs an {@code ApiApp} object. This default (i.e., no argument)
     * constructor is executed in Step 2 of the JavaFX Application Life-Cycle.
     */
    public ApiApp() {

        // Initialize UI containers and elements
        root = new VBox(20);
        contentBox = new HBox(20);
        movieDetailsVBox = new VBox(15);
        streamingInfoVBox = new VBox(15);

        // Placeholder image URL and setup for the movie poster ImageView
        String placeholderUrl = "https://via.placeholder.com/300x450.png?text=No+Image";
        Image placeholderImage = new Image(placeholderUrl);
        posterImageView = new ImageView(placeholderImage);
        posterImageView.setPreserveRatio(true);
        posterImageView.setFitWidth(300);

        // Label for streaming information section
        streamingInfoLabel = new Label("Available to stream on:");
        streamingInfoLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        // Search bar and button setup
        HBox searchBox = new HBox(10);
        searchField = new TextField();
        searchField.setPromptText("Enter a movie title");
        searchButton = new Button("Search");
        searchBox.getChildren().addAll(searchField, searchButton);
        searchBox.setAlignment(Pos.CENTER);

        // Layout padding and alignment configuration
        movieDetailsVBox.setPadding(new javafx.geometry.Insets(10));
        streamingInfoVBox.setPadding(new javafx.geometry.Insets(10));
        contentBox.setPadding(new javafx.geometry.Insets(10));
        root.setPadding(new javafx.geometry.Insets(10));

        // Adding movie poster and streaming label to the UI containers
        movieDetailsVBox.getChildren().addAll(posterImageView);
        streamingInfoVBox.getChildren().addAll(streamingInfoLabel);

        contentBox.getChildren().addAll(movieDetailsVBox, streamingInfoVBox);
        root.getChildren().addAll(searchBox, contentBox);
        root.setAlignment(Pos.TOP_CENTER);
    } //ApiApp

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Movie Info App");

        // Setting the action for the search button
        searchButton.setOnAction(event -> searchMovie());

        // Setting up the scene and showing the stage
        scene = new Scene(root, 700, 625);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnCloseRequest(event -> Platform.exit());
        stage.show();
    } //start

    /**
     * Searches for a movie by its title.
     * Makes API requests to OMDB to retrieve movie details.
     */
    private void searchMovie() {
        String movieTitle = searchField.getText();
        if (!movieTitle.isEmpty()) {
            try {
                // URL encode the movie title and fetch movie details from OMDB API
                String encodedTitle = java.net.URLEncoder.encode(movieTitle, "UTF-8");
                String omdbUrl = "http://www.omdbapi.com/?t=" + encodedTitle +
                    "&apikey=" + OMDB_API_KEY;

                HttpRequest omdbRequest = HttpRequest.newBuilder()
                    .uri(URI.create(omdbUrl))
                    .GET()
                    .build();

                HttpResponse<String> omdbResponse = HttpClient.newHttpClient()
                    .send(omdbRequest, HttpResponse.BodyHandlers.ofString());

                // Parse the OMDB response using Gson
                Gson gson = new Gson();
                OMDbResponse omdbMovie = gson.fromJson(omdbResponse.body(), OMDbResponse.class);

                if ("True".equals(omdbMovie.Response)) {
                    // Update movie details and fetch the poster image
                    String title = omdbMovie.Title;
                    String year = omdbMovie.Year;
                    String posterUrl = omdbMovie.Poster;

                    updateMovieDetails(title, year);

                    Image posterImage = new Image(posterUrl, false);
                    posterImageView.setImage(posterImage);

                    // Fetch streaming information after fetching movie details
                    fetchStreamingInfo(movieTitle);
                } else {
                    // Handle the case where the movie is not found
                    movieDetailsFlow.getChildren().clear();
                    movieDetailsFlow.getChildren().add(new Text("Movie not found."));
                    posterImageView.setImage(null);
                    streamingInfoVBox.getChildren().clear();
                    streamingInfoVBox.getChildren().add(
                        new Label("Streaming Information: Not available"));
                } //else
            } catch (Exception e) {
                // Handle any errors during the API request process
                movieDetailsFlow.getChildren().clear();
                movieDetailsFlow.getChildren().add(new Text("Error fetching movie data."));
                streamingInfoVBox.getChildren().clear();
                streamingInfoVBox.getChildren().add(
                    new Label("Streaming Information: Not available"));
                e.printStackTrace();
            } //catch
        } //if
    } //searchMovie

    /**
     * Fetches streaming information for a given movie title.
     *
     * @param movieTitle the title of the movie
     */
    private void fetchStreamingInfo(String movieTitle) {
        try {
            String encodedTitle = java.net.URLEncoder.encode(movieTitle, "UTF-8");
            WatchModeMovie movieData = fetchMovieData(encodedTitle);

            if (movieData != null) {
                WatchModeListing[] streamingData = fetchStreamingData(movieData.id);
                updateStreamingInfoUI(streamingData);
            } else {
                displayNoStreamingInfo();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Retrieves movie data from the WatchMode API based on the movie title.
     *
     * @param encodedTitle the URL-encoded title of the movie
     * @return the {@code WatchModeMovie} object containing movie details
     * @throws Exception if an error occurs during the API request
     */
    private WatchModeMovie fetchMovieData(String encodedTitle) throws Exception {
        String watchModeSearchUrl = "https://api.watchmode.com/v1/search/?apiKey=" +
            WATCHMODE_API_KEY + "&search_field=name&search_value=" + encodedTitle;

        HttpRequest watchModeRequest = HttpRequest.newBuilder()
            .uri(URI.create(watchModeSearchUrl))
            .GET()
            .build();

        HttpResponse<String> watchModeResponse = HttpClient.newHttpClient().send(
            watchModeRequest, HttpResponse.BodyHandlers.ofString());

        Gson gson = new Gson();
        WatchModeSearchResponse watchModeSearch = gson.fromJson(
            watchModeResponse.body(), WatchModeSearchResponse.class);

        if (watchModeSearch != null && watchModeSearch.title_results != null &&
            !watchModeSearch.title_results.isEmpty()) {
            return watchModeSearch.title_results.get(0);
        }
        return null;
    }

    /**
     * Retrieves streaming data for a movie using its ID from the WatchMode API.
     *
     * @param movieId the ID of the movie in the WatchMode API
     * @return an array of {@code WatchModeListing} objects containing streaming details
     * @throws Exception if an error occurs during the API request
     */
    private WatchModeListing[] fetchStreamingData(String movieId) throws Exception {
        String streamingUrl = "https://api.watchmode.com/v1/title/" + movieId +
            "/sources/?apiKey=" + WATCHMODE_API_KEY;

        HttpRequest streamingRequest = HttpRequest.newBuilder()
            .uri(URI.create(streamingUrl))
            .GET()
            .build();

        HttpResponse<String> streamingResponse = HttpClient.newHttpClient().send(
            streamingRequest, HttpResponse.BodyHandlers.ofString());

        Gson gson = new Gson();
        return gson.fromJson(streamingResponse.body(), WatchModeListing[].class);
    }

    /**
     * Updates the UI with streaming platform information for a movie.
     * Clears the existing streaming information and adds platforms from the provided data.
     *
     * @param streamingData an array of {@code WatchModeListing} objects containing 
     * streaming platform information
     */
    private void updateStreamingInfoUI(WatchModeListing[] streamingData) {
        streamingInfoVBox.getChildren().clear();
        streamingInfoVBox.getChildren().add(streamingInfoLabel);

        if (streamingData != null && streamingData.length > 0) {
            String[] targetPlatforms = {
                "Netflix", "Hulu", "Prime Video", "Disney+",
                "MAX", "Peacock", "Paramount Plus", "AppleTV", "YouTube"
            };

            Set<String> addedPlatforms = new HashSet<>();
            for (WatchModeListing listing : streamingData) {
                if (listing != null && listing.name != null && !listing.name.isEmpty() &&
                    Arrays.asList(targetPlatforms).contains(listing.name) &&
                    addedPlatforms.add(listing.name)) {

                    HBox platformBox = createPlatformBox(listing.name);
                    streamingInfoVBox.getChildren().add(platformBox);
                }
            }
        } else {
            displayNoStreamingInfo();
        }
    }

    /**
     * Creates a UI box displaying the name and logo of a streaming platform.
     *
     * @param platformName the name of the streaming platform
     * @return a {@code HBox} containing the platform's name and logo
     */
    private HBox createPlatformBox(String platformName) {
        HBox platformBox = new HBox(10);

        String logoUrl = getPlatformLogoUrl(platformName);
        if (logoUrl != null) {
            ImageView logoView = new ImageView(new Image(logoUrl));
            logoView.setFitWidth(70);
            logoView.setFitHeight(70);
            platformBox.getChildren().add(logoView);
        }

        Label platformLabel = new Label(platformName);
        platformLabel.setFont(Font.font("Arial", FontWeight.SEMI_BOLD, 14));
        platformLabel.setAlignment(Pos.CENTER);
        platformLabel.setMaxHeight(Double.MAX_VALUE);
        platformBox.getChildren().add(platformLabel);

        return platformBox;
    }

    /**
     * Displays a message in the UI when no streaming information is available.
     * Clears the current streaming information and displays a default message.
     */
    private void displayNoStreamingInfo() {
        streamingInfoVBox.getChildren().clear();
        streamingInfoVBox.getChildren().add(new Label("No streaming info available"));
    }


    /**
     * Updates the UI with the movie title and year.
     *
     * @param title the title of the movie
     * @param year the release year of the movie
     */
    private void updateMovieDetails(String title, String year) {
        Text titleBold = new Text("Title: ");
        titleBold.setStyle("-fx-font-weight: bold;");

        Text titleText = new Text(title + "\n");

        Text yearBold = new Text("Year: ");
        yearBold.setStyle("-fx-font-weight: bold;");

        Text yearText = new Text(year);

        movieDetailsFlow = new TextFlow(titleBold, titleText, yearBold, yearText);
        movieDetailsVBox.getChildren().clear();
        movieDetailsVBox.getChildren().addAll(posterImageView, movieDetailsFlow);
    } //updateMovieDetails

    /**
     * Returns the URL of the logo for the specified streaming platform.
     *
     * @param platformName the name of the streaming platform
     * @return the URL of the logo for the specified platform, or {@code null} if the 
     * platform is not recognized
     */
    private String getPlatformLogoUrl(String platformName) {
        switch (platformName) {
        case "Netflix":
            return "https://static.vecteezy.com/system/resources/thumbnails/019/956/198/small_2x/" +
                "netflix-transparent-netflix-free-free-png.png";
        case "Hulu":
            return "https://uxwing.com/wp-content/themes/uxwing/download/brands-and-social-media/" +
                "hulu-icon.png";
        case "Prime Video":
            return "https://seeklogo.com/images/A/" +
                "amazon-prime-video-logo-6BB6062D90-seeklogo.com.png";
        case "Disney+":
            return "https://images.squarespace-cdn.com/content/v1/6481e2427d99a120d7205507/" +
                "da199d5e-e6ce-48c6-af20-0bb874e27b80/Button_SQUARE.png";
        case "MAX":
            return "https://logodownload.org/wp-content/uploads/2024/03/max-logo-0.png";
        case "Peacock":
            return "https://logodownload.org/wp-content/uploads/2022/12/peacock-logo-0.png";
        case "Paramount Plus":
            return "https://logodownload.org/wp-content/uploads/2021/03/paramount-plus-logo-0.png";
        case "AppleTV":
            return "https://logodownload.org/wp-content/uploads/2023/05/apple-tv-logo-0.png";
        case "YouTube":
            return "https://static.vecteezy.com/system/resources/thumbnails/018/930/575/small_2x/" +
                "youtube-logo-youtube-icon-transparent-free-png.png";
        default:
            return null;
        } //switch
    } //getPlatformLogoUrl

    /**
     * The main method to launch the JavaFX application.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    } //main

    /**
     * Represents the response from the OMDB API.
     * Contains basic movie information such as title, year, poster URL, and response status.
     */
    class OMDbResponse {
        String Title;
        String Year;
        String Poster;
        String Response;
    } //OMDbResponse

    /**
     * Represents the search response from the WatchMode API.
     * Contains a list of movie results that match the search query.
     */
    class WatchModeSearchResponse {
        List<WatchModeMovie> title_results;
    } //WatchModeSearchResponse

    /**
     * Represents a movie object from the WatchMode API.
     * Contains the unique identifier of the movie.
     */
    class WatchModeMovie {
        String id;
    } //WatchModeMovie

    /**
     * Represents a streaming platform listing from the WatchMode API.
     * Contains the name of the platform where the movie is available.
     */
    class WatchModeListing {
        String name;
    } //WatchModeListing
} //ApiApp
