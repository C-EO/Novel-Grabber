package system.library;

import grabber.CLI;
import grabber.GrabberUtils;
import grabber.Novel;
import system.data.Settings;
import system.init;
import system.data.library.LibrarySettings;
import system.data.library.LibraryNovel;
import system.notifications.DesktopNotification;
import system.notifications.EmailNotification;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Handles continues polling of followed novels for new releases.
 */
public class LibrarySystem {
    private EmailNotification emailNotification;
    private LibrarySettings librarySettings;
    public ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public LibrarySystem() {
        librarySettings = LibrarySettings.getInstance();
        try {
            emailNotification = new EmailNotification();
        } catch (Exception e) {
            GrabberUtils.err("Could not establish connection to SMTP Server. Check email settings.");
        }
        if(Settings.getInstance().isPollingEnabled()) {
            scheduler.scheduleWithFixedDelay(this::run, 0, Settings.getInstance().getFrequency(), TimeUnit.MINUTES);
        }
    }

    /**
     * Checks each novel in library for new chapter releases.
     * Downloads new chapters and sends emails if selected.
     */
    private void run() {
        for(LibraryNovel libraryNovel: librarySettings.getStarredNovels()) {
            GrabberUtils.info("Checking "+ libraryNovel.getTitle());

            // Build a Novel object from CLI parameters and fetch chapter list
            String[] cliParams = CLI.createArgsFromString(libraryNovel.getCliString());
            Novel autoNovel;
            try {
                autoNovel = Novel.builder().fromCLI(CLI.createParamsFromArgs(cliParams)).window("checker").build();
            } catch (ClassNotFoundException e) {
                GrabberUtils.err(e.getMessage());
                continue;
            } catch (IOException e) {
                GrabberUtils.err(e.getMessage(), e);
                continue;
            }
            autoNovel.check();

            libraryNovel.setNewestChapter(autoNovel.chapterList.size());

            // Difference between last local chapter and newest released online
            int chapterDifference = libraryNovel.getNewestChapterNumber() - libraryNovel.getLastLocalChapterNumber();
            // Download new chapters if releases are past set threshold
            if(chapterDifference >= libraryNovel.getThreshold() && libraryNovel.isAutoDownloadEnabled()) {
                autoDownload(libraryNovel, autoNovel);
                // Send EPUB as email attachment
                if(libraryNovel.isSendAttachmentEnabled() && emailNotification != null) {
                    emailNotification.sendAttachment(autoNovel);
                }
            }
            // Send notifications
            if(chapterDifference > 0 && libraryNovel.notificationsEnabled()) {
                if(libraryNovel.isSendEmailNotification() && emailNotification != null) {
                    emailNotification.sendNotification(autoNovel);
                }
                if(libraryNovel.isSendDesktopNotification()) {
                    DesktopNotification.sendChapterReleaseNotification(libraryNovel, autoNovel);
                }
            }
            // Update last local chapter number to newest
            if(libraryNovel.isUpdateLast()) {
                libraryNovel.setLastChapter(autoNovel.chapterList.size());
            }
        }
        // Update library gui
        if(init.gui != null) {
            init.gui.buildLibrary();
        }
        // Write changes to file
        librarySettings.save();
    }

    /**
     * Downloads and creates EPUB of new chapters.
     */
    private static void autoDownload(LibraryNovel libraryNovel, Novel autoNovel) {
        // Set chapter range
        autoNovel = Novel.modifier(autoNovel)
                .firstChapter(libraryNovel.getLastLocalChapterNumber())
                .lastChapter(autoNovel.chapterList.size())
                .build();

        try {
            autoNovel.downloadChapters();
            autoNovel.output();
            // downloadChapters throws an Exception when grabbing was stopped midway, not possible here
        } catch (InterruptedException e) {
            GrabberUtils.err(e.getMessage(), e);
        }
    }
}
