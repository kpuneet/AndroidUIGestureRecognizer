package it.sephiroth.android.library.uigestures

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.view.View

import java.util.HashSet
import java.util.LinkedHashSet

/**
 * @author alessandro crugnola
 */
class UIGestureRecognizerDelegate(private var mCallback: Callback?) {

    private var mView: View? = null
    /**
     * Enable/Disable any registered gestures
     *
     * @param enabled
     */
    var isEnabled = true
        set(enabled) {
            field = enabled
            if (!isEnabled) {
                stopListeningView()
            } else {
                startListeningView(mView!!)
            }
        }

    private val mSet = LinkedHashSet<UIGestureRecognizer>()

    interface Callback {
        /**
         * Asks the delegate if a gesture recognizer should begin interpreting touches.
         *
         * @param recognizer the current recognizer
         * @return true if the recognizer should begin interpreting touches.
         * @see  [
         * https://developer.apple.com/reference/uikit/uigesturerecognizerdelegate/1624213-gesturerecognizershouldbegin](https://developer.apple.com/reference/uikit/uigesturerecognizerdelegate/1624213-gesturerecognizershouldbegin)
         */
        fun shouldBegin(recognizer: UIGestureRecognizer): Boolean

        /**
         * Asks the delegate if two gesture recognizers should be allowed to recognize gestures simultaneously.<br></br>
         * true to allow both gestureRecognizer and otherGestureRecognizer to recognize their gestures simultaneously. The
         * default implementation returns false.
         *
         * @param recognizer the first recognizer
         * @param other      the second recognizer
         * @return true if both recognizers shouls be recognized simultaneously
         * @see [
         * https://developer.apple.com/reference/uikit/uigesturerecognizerdelegate/1624208-gesturerecognizer](https://developer.apple.com/reference/uikit/uigesturerecognizerdelegate/1624208-gesturerecognizer)
         */
        fun shouldRecognizeSimultaneouslyWithGestureRecognizer(recognizer: UIGestureRecognizer, other: UIGestureRecognizer): Boolean

        /**
         * Ask the delegate if a gesture recognizer should receive an object representing a touch.
         *
         * @param recognizer the recognizer that should receive the touch
         * @return true if the recognizer should receive the motion event
         * @see [
         * https://developer.apple.com/reference/uikit/uigesturerecognizerdelegate/1624214-gesturerecognizer](https://developer.apple.com/reference/uikit/uigesturerecognizerdelegate/1624214-gesturerecognizer)
         */
        fun shouldReceiveTouch(recognizer: UIGestureRecognizer): Boolean
    }

    /**
     * @param callback set the optional callback
     * @since 1.0.0
     */
    fun setCallback(callback: Callback) {
        this.mCallback = callback
    }

    /**
     * @param recognizer add a new gesture recognizer to the chain
     * @since 1.0.0
     */
    fun addGestureRecognizer(recognizer: UIGestureRecognizer) {
        recognizer.delegate = this
        mSet.add(recognizer)
    }

    /**
     * @param recognizer remove a previously added gesture recognizer
     * @return true if succesfully removed from the list
     * @since 1.0.0
     */
    fun removeGestureRecognizer(recognizer: UIGestureRecognizer): Boolean {
        if (mSet.remove(recognizer)) {
            recognizer.delegate = null
            recognizer.clearStateListeners()
            return true
        }
        return false
    }

    /**
     * Remove all the gesture recognizers currently associated with the delegate
     *
     * @since 1.0.0
     */
    fun clear() {
        for (uiGestureRecognizer in mSet) {
            uiGestureRecognizer.delegate = null
            uiGestureRecognizer.clearStateListeners()
        }
        mSet.clear()
    }

    /**
     * Forward the view's touchEvent
     *
     * @param view  the view that generated the event
     * @param event the motion event
     * @return true if handled
     * @since 1.0.0
     */
    fun onTouchEvent(view: View, event: MotionEvent): Boolean {
        var handled = false

        // TODO: each recognizer should prepare its internal status here
        // but don't execute any action
        for (recognizer in mSet) {
            if (shouldReceiveTouch(recognizer)) {
                handled = handled or recognizer.onTouchEvent(event)
            } else {
                handled = handled or recognizer.onTouchEvent(event)
            }
        }

        // TODO: here we need another loop to tell each recognizer to execute its action

        return handled
    }

    /**
     * Helper method to start listening for touch events. Use this instead
     * of [.onTouchEvent]
     *
     * @param view
     */
    fun startListeningView(view: View) {
        stopListeningView()

        mView = view
        mView!!.setOnTouchListener { v, event -> onTouchEvent(v, event) }
    }

    /**
     * Stop listening for touch events on the associated view
     */
    fun stopListeningView() {
        if (null != mView) {
            mView!!.setOnTouchListener(null)
            mView = null
        }
    }

    fun shouldRecognizeSimultaneouslyWithGestureRecognizer(recognizer: UIGestureRecognizer): Boolean {
        Log.i(javaClass.simpleName, "shouldRecognizeSimultaneouslyWithGestureRecognizer($recognizer)")
        if (mSet.size == 1) {
            return true
        }

        var result = true
        for (other in mSet) {
            if (other !== recognizer) {
                Log.v(javaClass.simpleName, "other: " + other + ", other.began: " + other.hasBeganFiringEvents())
                if (other.hasBeganFiringEvents()) {
                    result = result and (null != mCallback && mCallback!!.shouldRecognizeSimultaneouslyWithGestureRecognizer(recognizer, other))
                }
            }
        }
        Log.v(javaClass.simpleName, "result: $result")
        return result
    }

    fun shouldBegin(recognizer: UIGestureRecognizer): Boolean {
        return null == mCallback || mCallback!!.shouldBegin(recognizer)
    }

    private fun shouldRecognizeSimultaneouslyWithGestureRecognizer(
            current: UIGestureRecognizer, recognizer: UIGestureRecognizer): Boolean {
        return null == mCallback || mCallback!!.shouldRecognizeSimultaneouslyWithGestureRecognizer(current, recognizer)
    }

    private fun shouldReceiveTouch(recognizer: UIGestureRecognizer): Boolean {
        return null == mCallback || mCallback!!.shouldReceiveTouch(recognizer)
    }
}
