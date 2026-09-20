package com.tivo.kmttg.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tivo.kmttg.main.config;

// Pins the filesystem helpers in util.file. Most of them swallow the
// exception and hand back a Boolean or a 0, so callers all over kmttg branch
// on the return value alone and a change in what a failure returns is silent.
// The failure cases are as much the point here as the happy ones. Everything
// runs inside a throwaway temp dir, with the config directories these read
// pointed at it and restored afterwards.
public class FileUtilTest {

   @TempDir
   Path tmp;

   private Path mpegDir, cutDir, tmpDir;
   private String prevTmpDir, prevMpegDir, prevMpegCutDir;
   private Boolean prevGuiMode;

   @BeforeEach
   public void redirectConfigDirs() throws IOException {
      mpegDir = Files.createDirectories(tmp.resolve("mpeg"));
      cutDir = Files.createDirectories(tmp.resolve("cut"));
      tmpDir = Files.createDirectories(tmp.resolve("temp"));

      prevTmpDir = config.tmpDir;
      prevMpegDir = config.mpegDir;
      prevMpegCutDir = config.mpegCutDir;
      prevGuiMode = config.GUIMODE;

      config.tmpDir = tmpDir.toString();
      config.mpegDir = mpegDir.toString();
      config.mpegCutDir = cutDir.toString();
      // The error paths call log.error(), which posts to the Swing gui when this is on
      config.GUIMODE = false;
   }

   @AfterEach
   public void restoreConfigDirs() {
      config.tmpDir = prevTmpDir;
      config.mpegDir = prevMpegDir;
      config.mpegCutDir = prevMpegCutDir;
      config.GUIMODE = prevGuiMode;
   }

   @Test
   public void isFileAcceptsFilesOnlyNotDirsOrMissingPaths() throws IOException {
      Path f = write("a.txt", "x");
      assertTrue(file.isFile(f.toString()));
      assertFalse(file.isFile(tmp.toString()), "a directory is not a file");
      assertFalse(file.isFile(tmp.resolve("gone.txt").toString()));
   }

   @Test
   public void isDirAcceptsDirsOnlyNotFilesOrMissingPaths() throws IOException {
      Path f = write("a.txt", "x");
      assertTrue(file.isDir(tmp.toString()));
      assertFalse(file.isDir(f.toString()));
      assertFalse(file.isDir(tmp.resolve("nodir").toString()));
   }

   // Both catch the NPE from new File(null) rather than letting it out
   @Test
   public void isFileAndIsDirReturnFalseForNull() {
      assertFalse(file.isFile(null));
      assertFalse(file.isDir(null));
   }

   @Test
   public void sizeReportsByteCountAndZeroForAMissingFile() throws IOException {
      Path f = write("a.txt", "12345");
      assertEquals(5, file.size(f.toString()));
      assertEquals(0, file.size(tmp.resolve("gone.txt").toString()));
      assertEquals(0, file.size(null));
   }

   // size() is 0 for a file that is not there at all, so isEmpty() cannot tell
   // "empty" from "missing" - callers have to check isFile() themselves
   @Test
   public void isEmptyIsTrueForAMissingFileAsWellAsAnEmptyOne() throws IOException {
      assertTrue(file.isEmpty(write("empty.txt", "").toString()));
      assertTrue(file.isEmpty(tmp.resolve("gone.txt").toString()));
      assertFalse(file.isEmpty(write("full.txt", "x").toString()));
   }

   @Test
   public void freeSpaceIsZeroForAnythingButADirectory() throws IOException {
      assertTrue(file.freeSpace(tmp.toString()) > 0, "no free space reported for a real dir");
      assertEquals(0, file.freeSpace(write("a.txt", "x").toString()));
      assertEquals(0, file.freeSpace(tmp.resolve("nodir").toString()));
   }

   @Test
   public void createMakesAnEmptyFileAndRefusesToClobberAnExistingOne() {
      String f = tmp.resolve("new.txt").toString();
      assertTrue(file.create(f));
      assertTrue(file.isFile(f));
      assertEquals(0, file.size(f));
      assertFalse(file.create(f), "create() must not report success for an existing file");
   }

   @Test
   public void createFailsWhenTheParentDirectoryIsMissing() {
      assertFalse(file.create(tmp.resolve("nodir").resolve("new.txt").toString()));
   }

   @Test
   public void deleteRemovesAnExistingFile() throws IOException {
      Path f = write("a.txt", "x");
      assertTrue(file.delete(f.toString()));
      assertFalse(Files.exists(f));
   }

   @Test
   public void deleteReturnsFalseForAMissingFile() {
      assertFalse(file.delete(tmp.resolve("gone.txt").toString()));
      assertFalse(file.delete(null));
   }

   @Test
   public void deleteTakesAnEmptyDirectoryButNotAFullOne() throws IOException {
      Path full = Files.createDirectories(tmp.resolve("full"));
      Files.write(full.resolve("child.txt"), "x".getBytes(StandardCharsets.UTF_8));
      assertFalse(file.delete(full.toString()), "delete() emptied a non-empty directory");
      assertTrue(Files.exists(full));

      Path empty = Files.createDirectories(tmp.resolve("empty"));
      assertTrue(file.delete(empty.toString()));
   }

   @Test
   public void deleteDirRemovesANestedTree() throws IOException {
      Path root = Files.createDirectories(tmp.resolve("tree/a/b"));
      Files.write(root.resolve("deep.txt"), "x".getBytes(StandardCharsets.UTF_8));
      Files.write(tmp.resolve("tree/top.txt"), "x".getBytes(StandardCharsets.UTF_8));

      assertTrue(file.deleteDir(tmp.resolve("tree").toFile()));
      assertFalse(Files.exists(tmp.resolve("tree")));
   }

   @Test
   public void deleteDirReturnsFalseForAMissingDirectory() {
      assertFalse(file.deleteDir(tmp.resolve("nodir").toFile()));
   }

   // Takes the path of a FILE and creates the directories above it
   @Test
   public void createDirIfNeededMakesTheWholeParentChain() {
      Path target = tmp.resolve("x").resolve("y").resolve("z.txt");
      assertTrue(file.createDirIfNeeded(target.toString()));
      assertTrue(Files.isDirectory(tmp.resolve("x").resolve("y")));
      // Already there the second time round, and that is still success
      assertTrue(file.createDirIfNeeded(target.toString()));
   }

   // A bare name has no directory part, so there is nothing to create and the
   // empty path cannot be made - callers have to pass a full path
   @Test
   public void createDirIfNeededFailsForABareFileName() {
      assertFalse(file.createDirIfNeeded("z.txt"));
   }

   @Test
   public void copyDuplicatesFileContent() throws IOException {
      Path source = write("source.bin", "hello there");
      Path dest = tmp.resolve("dest.bin");
      assertTrue(file.copy(source.toString(), dest.toString()));
      assertEquals("hello there", new String(Files.readAllBytes(dest), StandardCharsets.UTF_8));
      assertTrue(Files.exists(source), "copy() must leave the source alone");
   }

   // The read loop stops on <= 0, so a zero byte source has to still produce
   // the destination file rather than nothing at all
   @Test
   public void copyOfAnEmptyFileStillCreatesTheDestination() throws IOException {
      Path source = write("empty.bin", "");
      Path dest = tmp.resolve("empty-copy.bin");
      assertTrue(file.copy(source.toString(), dest.toString()));
      assertTrue(Files.exists(dest));
      assertEquals(0, file.size(dest.toString()));
   }

   @Test
   public void copyOverwritesAnExistingDestination() throws IOException {
      Path source = write("source.bin", "new");
      Path dest = write("dest.bin", "much longer old content");
      assertTrue(file.copy(source.toString(), dest.toString()));
      assertEquals("new", new String(Files.readAllBytes(dest), StandardCharsets.UTF_8));
   }

   // Nothing is opened for writing until the source opens, so a failure here
   // must not leave a half written destination behind
   @Test
   public void copyOfAnUnreadableSourceFailsAndWritesNothing() {
      Path dest = tmp.resolve("dest.bin");
      assertFalse(file.copy(tmp.resolve("gone.bin").toString(), dest.toString()));
      assertFalse(Files.exists(dest), "a destination was created for a missing source");

      assertFalse(file.copy(tmp.toString(), dest.toString()), "copied a directory");
      assertFalse(Files.exists(dest));
   }

   // The destination opens after the source, so a bad destination used to leave the source
   // stream open and Windows would not let go of the file afterwards
   @Test
   public void aFailedCopyReleasesTheSourceFile() throws IOException {
      Path source = write("source.bin", "hello there");
      assertFalse(file.copy(source.toString(), tmp.resolve("gone").resolve("dest.bin").toString()));
      Files.delete(source);
   }

   @Test
   public void renameMovesAFile() throws IOException {
      Path source = write("old.txt", "x");
      Path dest = tmp.resolve("new.txt");
      assertTrue(file.rename(source.toString(), dest.toString()));
      assertFalse(Files.exists(source));
      assertTrue(Files.exists(dest));
   }

   @Test
   public void renameOfAMissingFileFails() {
      assertFalse(file.rename(tmp.resolve("gone.txt").toString(), tmp.resolve("new.txt").toString()));
   }

   // renameTo() will not replace an existing target on Windows, so callers
   // that want a replace have to delete the target first
   @Test
   public void renameOntoAnExistingFileFails() throws IOException {
      Path source = write("old.txt", "source");
      Path dest = write("new.txt", "target");
      assertFalse(file.rename(source.toString(), dest.toString()));
      assertEquals("target", new String(Files.readAllBytes(dest), StandardCharsets.UTF_8));
      assertTrue(Files.exists(source));
   }

   @Test
   public void makeTempFileCreatesTheFileInTheConfiguredTmpDir() {
      String path = file.makeTempFile("kmttg");
      assertNotNull(path);
      assertTrue(file.isFile(path));
      assertEquals(tmpDir.toString(), string.dirname(path));
      assertTrue(string.basename(path).startsWith("kmttg"));
      assertTrue(path.endsWith(".tmp"), "default suffix is .tmp, got " + path);
   }

   @Test
   public void makeTempFileHonorsAnExplicitSuffix() {
      String path = file.makeTempFile("kmttg", ".ts");
      assertNotNull(path);
      assertTrue(file.isFile(path));
      assertTrue(path.endsWith(".ts"), "explicit suffix ignored, got " + path);
   }

   @Test
   public void makeTempFileReturnsNullWhenTmpDirDoesNotExist() {
      config.tmpDir = tmp.resolve("nodir").toString();
      assertNull(file.makeTempFile("kmttg"));
      assertNull(file.makeTempFile("kmttg", ".ts"));
   }

   @Test
   public void cleanUpFilesDeletesOnlyThePrefixedFilesInMpegDir() throws IOException {
      Files.write(mpegDir.resolve("tmp_one.mpg"), "x".getBytes(StandardCharsets.UTF_8));
      Files.write(mpegDir.resolve("tmp_two.mpg"), "x".getBytes(StandardCharsets.UTF_8));
      Files.write(mpegDir.resolve("keep.mpg"), "x".getBytes(StandardCharsets.UTF_8));
      // Matches the prefix but is a directory, so the isFile() guard spares it
      Files.createDirectories(mpegDir.resolve("tmp_dir"));

      file.cleanUpFiles("tmp_");

      assertFalse(Files.exists(mpegDir.resolve("tmp_one.mpg")));
      assertFalse(Files.exists(mpegDir.resolve("tmp_two.mpg")));
      assertTrue(Files.exists(mpegDir.resolve("keep.mpg")));
      assertTrue(Files.exists(mpegDir.resolve("tmp_dir")));
   }

   // The cut dir is searched before the mpeg dir, and inside each the _cut
   // copy is preferred - mpegCutDir may be the same directory as mpegDir
   @Test
   public void vrdreviewFileSearchPrefersTheCutFileInTheCutDir() throws IOException {
      Files.write(cutDir.resolve("Show_cut.mpg"), "x".getBytes(StandardCharsets.UTF_8));
      Files.write(cutDir.resolve("Show.mpg"), "x".getBytes(StandardCharsets.UTF_8));
      Files.write(mpegDir.resolve("Show.mpg"), "x".getBytes(StandardCharsets.UTF_8));

      assertEquals(cutDir + File.separator + "Show_cut.mpg", file.vrdreviewFileSearch("Show.mpg"));
   }

   @Test
   public void vrdreviewFileSearchFallsBackToTheMpegDir() throws IOException {
      Files.write(mpegDir.resolve("Show.ts"), "x".getBytes(StandardCharsets.UTF_8));
      assertEquals(mpegDir + File.separator + "Show.ts", file.vrdreviewFileSearch("Show.ts"));
   }

   // VideoReDo writes the reviewed copy next to the original as " (02)", and
   // that has to win over the untouched original
   @Test
   public void vrdreviewFileSearchPrefersTheSecondPassCopyOverTheOriginal() throws IOException {
      Files.write(mpegDir.resolve("Show (02).mpg"), "x".getBytes(StandardCharsets.UTF_8));
      Files.write(mpegDir.resolve("Show.mpg"), "x".getBytes(StandardCharsets.UTF_8));
      assertEquals(mpegDir + File.separator + "Show (02).mpg", file.vrdreviewFileSearch("Show.mpg"));
   }

   // The name may carry sub-folders from the kmttg file naming, and those are
   // resolved under the mpeg dir before the bare basename is tried
   @Test
   public void vrdreviewFileSearchHonorsSubFoldersInTheName() throws IOException {
      Path sub = Files.createDirectories(mpegDir.resolve("Series"));
      Files.write(sub.resolve("Show.mpg"), "x".getBytes(StandardCharsets.UTF_8));

      String startFile = "Series" + File.separator + "Show.mpg";
      assertEquals(sub + File.separator + "Show.mpg", file.vrdreviewFileSearch(startFile));
   }

   @Test
   public void vrdreviewFileSearchReturnsNullWhenNothingMatches() {
      assertNull(file.vrdreviewFileSearch("Missing.mpg"));
   }

   @Test
   public void csvFieldQuotesTheValueAndAppendsASeparator() {
      assertEquals("\"plain\",", file.csvField("plain"));
      assertEquals("\"with, comma\",", file.csvField("with, comma"));
      assertEquals("\"\",", file.csvField(""));
   }

   // Doubling is what makes an embedded quote survive the round trip
   @Test
   public void csvFieldDoublesEmbeddedQuotes() {
      assertEquals("\"say \"\"hi\"\"\",", file.csvField("say \"hi\""));
   }

   private Path write(String name, String content) throws IOException {
      Path p = tmp.resolve(name);
      Files.write(p, content.getBytes(StandardCharsets.UTF_8));
      return p;
   }
}
