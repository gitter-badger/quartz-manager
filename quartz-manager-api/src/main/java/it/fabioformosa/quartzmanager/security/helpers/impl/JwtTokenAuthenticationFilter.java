package it.fabioformosa.quartzmanager.security.helpers.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;


/**
 *   It finds the jwtToken into the request, it validates it and sets an @Authentication into the @SecurityContextHolder.
 *   If the request has a path included into the paths that must be skipped, it sets an anonymous authentication
 *
 *   It delegates the jwtToken retrieve to the @JwtTokenHelper that applies several strategies.
 *
 */
public class JwtTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenAuthenticationFilter.class);

  private static final String ROOT_MATCHER = "/";
  private static final String FAVICON_MATCHER = "/favicon.ico";
  private static final String HTML_MATCHER = "/**/*.html";
  private static final String CSS_MATCHER = "/**/*.css";
  private static final String JS_MATCHER = "/**/*.js";
  private static final String IMG_MATCHER = "/images/*";
  private static final String LOGIN_MATCHER = "/api/login";
  private static final String LOGOUT_MATCHER = "/api/logout";

  private static List<String> PATH_TO_SKIP = Arrays.asList(
      ROOT_MATCHER,
      HTML_MATCHER,
      FAVICON_MATCHER,
      CSS_MATCHER,
      JS_MATCHER,
      IMG_MATCHER,
      LOGIN_MATCHER,
      LOGOUT_MATCHER
      );

  private final JwtTokenHelper jwtTokenHelper;
  private final UserDetailsService userDetailsService;


  public JwtTokenAuthenticationFilter(JwtTokenHelper jwtTokenHelper, UserDetailsService userDetailsService) {
    super();
    this.jwtTokenHelper = jwtTokenHelper;
    this.userDetailsService = userDetailsService;
  }

  @Override
  public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

    String jwtToken = jwtTokenHelper.retrieveToken(request);
    if (jwtToken != null) {
      log.debug("Found a jwtToken into the request {}", request.getPathInfo());
      try {
        String username = jwtTokenHelper.getUsernameFromToken(jwtToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        JwtTokenBasedAuthentication authentication = new JwtTokenBasedAuthentication(userDetails);
        authentication.setToken(jwtToken);

        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (Exception e) {
        log.error("Authentication failed! an expected error occurred authenticating the request {}", request.getRequestURL());
        //        SecurityContextHolder.getContext().setAuthentication(new AnonAuthentication());
        //        log.error("Switched to Anonymous Authentication, "
        //            + "because an error occurred setting authentication in security context holder due to " + e.getMessage(), e);
      }
    }
    else if(skipPathRequest(request, PATH_TO_SKIP)) {
      log.debug("Detected a path to be skipped from authentication, so activated anonymous auth for {}", request.getRequestURL());
      SecurityContextHolder.getContext().setAuthentication(new AnonAuthentication());
    }
    else
      log.debug("Not found any jwtToken and the request hasn't a path to be skipped from auth. Path: {}", request.getRequestURL());

    chain.doFilter(request, response);
  }

  private boolean skipPathRequest(HttpServletRequest request, List<String> pathsToSkip ) {
    if(pathsToSkip == null)
      pathsToSkip = new ArrayList<String>();
    List<RequestMatcher> matchers = pathsToSkip.stream().map(path -> new AntPathRequestMatcher(path)).collect(Collectors.toList());
    OrRequestMatcher compositeMatchers = new OrRequestMatcher(matchers);
    return compositeMatchers.matches(request);
  }

}