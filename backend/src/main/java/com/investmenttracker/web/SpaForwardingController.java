package com.investmenttracker.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The SPA uses client-side routing, so a deep link such as {@code /holdings} arrives here as a
 * real HTTP request that no controller and no static file matches. Forwarding it to the bundle
 * lets React Router take over.
 *
 * <p>The pattern deliberately excludes paths containing a dot (static assets) and matches a single
 * segment only, which is every route declared in {@code App.tsx}. Multi-segment paths stay with
 * the API and the actuator.
 */
@Controller
class SpaForwardingController {

    @GetMapping("/{path:[^.]*}")
    String forwardToIndex() {
        return "forward:/index.html";
    }
}
