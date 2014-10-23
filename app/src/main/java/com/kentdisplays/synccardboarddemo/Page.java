/*******************************************************************************
 Copyright © 2014 Kent Displays, Inc.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 ******************************************************************************/

package com.kentdisplays.synccardboarddemo;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.SystemClock;

import com.google.common.primitives.Floats;
import com.sun.pdfview.decode.FlateDecode;

import org.apache.http.util.EncodingUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Object that encapsulates drawing a page generated from a Boogie Board Sync.
 */
public class Page {

    private static final float mDistance = 3f;
    private int mNumberOfPaths;
    private float[] mModel;

    private int mModelViewProjectionParam;
    private int mIsFloorParam;
    private int mModelParam;
    private int mModelViewParam;
    private int mPositionParam;
    private int mNormalParam;
    private int mColorParam;
    private int mGlProgram;

    private FloatBuffer mPageVertices;
    private FloatBuffer mPageNormals;
    private FloatBuffer mPageColors;

    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 3;

    /**
     * Sets up the drawing object data for use in an OpenGL ES context.
     *
     * @param is InputStream to the page to load the path data from.
     */
    public Page(InputStream is, int glProgram, int direction) {

        this.mModel = new float[16];
        this.mGlProgram = glProgram;

        // Calculate the coordinates from the given path.
        ArrayList<Path> paths = pathsFromSamplePageInputStream(is);
        float finalCoords[] = {};
        float finalNormals[] = {};
        float finalColors[] = {};
        mNumberOfPaths = paths.size();
        for(int i = 0; i < mNumberOfPaths; i++) {
            Path path = paths.get(i);
            float x1 = (path.x1 / 13942 * 2) - 1;
            float y1 = (path.y1 / 20280 * 2) - 1;
            float x2 = (path.x2 / 13942 * 2) - 1;
            float y2 = (path.y2 / 20280 * 2) - 1;
            float width = path.width / 3000;
            width = width < 0.013f ? 0.013f : width; // Width should be at least 0.013

            float distance = (float)Math.sqrt(Math.pow(x2-x1,2) + Math.pow(y2-y1,2));
            float angle = (float)Math.PI/2 - (float)Math.asin((x2-x1)/distance);
            float xdiff = (width/2)*(float)Math.sin(angle);
            float ydiff = (width/2)*(float)Math.cos(angle);

            float coords[] = {
                    x1 - xdiff, y1 - ydiff, 1.0f,   // top left
                    x2 - xdiff, y2 - ydiff, 1.0f,   // bottom left
                    x1 + xdiff, y1 + ydiff, 1.0f,   // top right
                    x2 - xdiff, y2 - ydiff, 1.0f,   // bottom left
                    x2 + xdiff, y2 + ydiff, 1.0f,   // bottom right
                    x1 + xdiff, y1 + ydiff, 1.0f,   // top right
            };

            float normals[] = {
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f,
            };

            float colors[] = {
                    0.2f, 0.709803922f, 0.898039216f, 1.0f,
                    0.2f, 0.709803922f, 0.898039216f, 1.0f,
                    0.2f, 0.709803922f, 0.898039216f, 1.0f,
                    0.2f, 0.709803922f, 0.898039216f, 1.0f,
                    0.2f, 0.709803922f, 0.898039216f, 1.0f,
                    0.2f, 0.709803922f, 0.898039216f, 1.0f,
            };

            finalCoords = Floats.concat(finalCoords, coords);
            finalNormals = Floats.concat(finalNormals, normals);
            finalColors = Floats.concat(finalColors, colors);
        }

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(finalCoords.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        mPageVertices = bbVertices.asFloatBuffer();
        mPageVertices.put(finalCoords);
        mPageVertices.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(finalNormals.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        mPageNormals = bbNormals.asFloatBuffer();
        mPageNormals.put(finalNormals);
        mPageNormals.position(0);

        ByteBuffer bbColors = ByteBuffer.allocateDirect(finalColors.length * 4);
        bbColors.order(ByteOrder.nativeOrder());
        mPageColors = bbColors.asFloatBuffer();
        mPageColors.put(finalColors);
        mPageColors.position(0);

        // Correctly place the page in the world.
        Matrix.setIdentityM(mModel, 0);
        switch(direction) {
            case 0:
                Matrix.translateM(mModel, 0, 0, 0, -mDistance); //Front.
                break;
            case 1:
                Matrix.translateM(mModel, 0, -mDistance, 0, 0); // Left.
                Matrix.rotateM(mModel, 0, 90,0,1f,0);
                break;
            case 2:
                Matrix.translateM(mModel, 0, 0, 0, mDistance); // Behind.
                Matrix.rotateM(mModel, 0, 180,0,1f,0);
                break;
            case 3:
                Matrix.translateM(mModel, 0, mDistance, 0, 0); // Right.
                Matrix.rotateM(mModel, 0, 270,0,1f,0);
                break;
        }
    }

    /**
     * Encapsulates the OpenGL ES instructions for drawing this page.
     *
     * @param perspective
     * @param view
     */
    public void draw(float[] perspective, float[] view) {
        mPositionParam = GLES20.glGetAttribLocation(mGlProgram, "a_Position");
        mNormalParam = GLES20.glGetAttribLocation(mGlProgram, "a_Normal");
        mColorParam = GLES20.glGetAttribLocation(mGlProgram, "a_Color");
        mModelViewProjectionParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVP");
        mIsFloorParam = GLES20.glGetUniformLocation(mGlProgram, "u_IsFloor");
        mModelParam = GLES20.glGetUniformLocation(mGlProgram, "u_Model");
        mModelViewParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVMatrix");

        // This is not the floor!
        GLES20.glUniform1f(mIsFloorParam, 0f);

        // Set the Model in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModel, 0);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        float[] modelView = new float[16];
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelView, 0, view, 0, mModel, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);

        // Set the ModelView in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(mModelViewParam, 1, false, modelView, 0);

        // Set the position of the cube
        GLES20.glVertexAttribPointer(mPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, mPageVertices);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(mModelViewProjectionParam, 1, false, modelViewProjection, 0);

        // Set the normal positions of the cube, again for shading
        GLES20.glVertexAttribPointer(mNormalParam, 3, GLES20.GL_FLOAT,
                false, 0, mPageNormals);

        GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false,
                0, mPageColors);

        // Animate over all the paths every 30 seconds.
        long time = SystemClock.uptimeMillis() % 30000L;
        int numberOfPathsToDraw = Math.round(mNumberOfPaths / 30000.0f * time);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, numberOfPathsToDraw * 6);
    }

    /**
     * Decodes an input stream from a file into Path objects that can be used to draw the page.
     */
    private static ArrayList<Path> pathsFromSamplePageInputStream(InputStream inputStream) {
        ArrayList<Path> paths = new ArrayList<Path>();
        try {
            // Retrieve a byte array from the sample page.
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > -1) {
                baos.write(buffer, 0, len);
            }
            baos.flush();
            byte[] byteArray = baos.toByteArray();

            // Decode the path data from the sample page.
            String rawString = EncodingUtils.getAsciiString(byteArray);
            int startIndex = rawString.indexOf("<</Length 13 0 R/Filter /FlateDecode>>") + 46;
            int endIndex = rawString.indexOf("endstream", startIndex) - 1;
            byte []flateEncodedByteArray = Arrays.copyOfRange(byteArray, startIndex, endIndex);
            net.sf.andpdf.nio.ByteBuffer flateEncodedBuffer = net.sf.andpdf.nio.ByteBuffer.NEW(flateEncodedByteArray);
            net.sf.andpdf.nio.ByteBuffer decodedBuffer = FlateDecode.decode(null, flateEncodedBuffer, null);
            String decodedString = new String(decodedBuffer.array(),"UTF-8");

            // Break every line of the decoded string into usable objects.
            String[] stringArray = decodedString.split("\\r?\\n");
            for(int i = 0; i < stringArray.length; i++) {
                String[] components = stringArray[i].split(" ");
                Path path = new Path();
                path.y1 = Float.valueOf(components[2]);
                path.x1 = Float.valueOf(components[3]);
                path.y2 = Float.valueOf(components[5]);
                path.x2 = Float.valueOf(components[6]);
                path.width = Float.valueOf(components[0]);
                paths.add(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return paths;
    }
}
